package com.misgus.pengu.data.backend

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.BackendProfile
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AccountState
import com.misgus.pengu.core.repository.SignInMethod
import com.misgus.pengu.core.repository.SignInRequest
import com.misgus.pengu.data.store.AddonSnapshotStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NuvioAddonRepositoryTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun cachesAddonsAndRefetchesWhenTheProfileChanges() =
        runTest {
            var addonCalls = 0
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/.well-known/nuvio") ->
                            respond(
                                """{"version":1,"service":"nuvio","backend_url":"https://x","publishable_key":"anon"}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        path.contains("/auth/v1/token") ->
                            respond(
                                """{"access_token":"a","refresh_token":"r","user":{"id":"u1","email":"me@example.com"},"expires_at":9999999999}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        path.contains("/rest/v1/addons") -> {
                            addonCalls++
                            respond(
                                """[{"id":"a1","url":"https://addon/manifest.json","name":"Addon","enabled":true,"sort_order":0,"profile_id":1}]""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        }
                        else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                    }
                }
            val stack = nuvioTestStack(engine)
            val account = FakeAccount(profileKey = "p1", profileId = 1)
            val repository = NuvioAddonRepository(stack.rest, account)
            stack.auth.signIn("me@example.com", "secret")

            val first = repository.all()
            val second = repository.all()

            assertEquals(1, addonCalls)
            assertEquals(1, first.size)
            assertEquals(first, second)

            // A different profile is a different addon list, so the cache misses.
            account.setProfile(profileKey = "p2", profileId = 2)
            repository.all()

            assertEquals(2, addonCalls)
        }

    @Test
    fun servesTheLocalSnapshotBeforeTheBackendAnswers() =
        runTest {
            var addonCalls = 0
            val engine =
                MockEngine { request ->
                    val path = request.url.encodedPath
                    when {
                        path.endsWith("/.well-known/nuvio") ->
                            respond(
                                """{"version":1,"service":"nuvio","backend_url":"https://x","publishable_key":"anon"}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        path.contains("/auth/v1/token") ->
                            respond(
                                """{"access_token":"a","refresh_token":"r","user":{"id":"u1","email":"me@example.com"},"expires_at":9999999999}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        path.contains("/rest/v1/addons") -> {
                            addonCalls++
                            respond(
                                """[{"id":"a1","url":"https://addon/manifest.json","name":"Addon","enabled":true,"sort_order":0,"profile_id":1}]""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        }
                        else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                    }
                }
            val stack = nuvioTestStack(engine)
            val account = FakeAccount(profileKey = "p1", profileId = 1)
            val store = MemoryAddonSnapshotStore(mapOf("p1" to listOf(Addon(id = "a0", url = "https://old/manifest.json"))))
            val repository = NuvioAddonRepository(stack.rest, account, store, backgroundScope)
            val changes = Channel<Unit>(Channel.UNLIMITED)
            backgroundScope.launch(Dispatchers.Default) { repository.changes.collect { changes.send(Unit) } }
            stack.auth.signIn("me@example.com", "secret")

            // The first catalog request can start right away: no backend read.
            assertEquals(listOf("a0"), repository.all().map(Addon::id))
            assertEquals(0, addonCalls)

            // The refresh runs off the test scheduler (it performs an HTTP call),
            // so wait for its result instead of advancing virtual time.
            withContext(Dispatchers.Default) {
                withTimeout(5.seconds) {
                    while (store.read("p1")?.map(Addon::id) != listOf("a1")) delay(10.milliseconds)
                    changes.receive()
                }
            }

            // The backend answer replaced the snapshot and announced the change.
            assertEquals(1, addonCalls)
            assertEquals(listOf("a1"), store.read("p1")?.map(Addon::id))
            assertEquals(listOf("a1"), repository.all().map(Addon::id))
        }

    private class MemoryAddonSnapshotStore(
        snapshots: Map<String, List<Addon>> = emptyMap(),
    ) : AddonSnapshotStore {
        private val snapshots = snapshots.toMutableMap()

        override suspend fun read(profileKey: String): List<Addon>? = snapshots[profileKey]

        override suspend fun write(
            profileKey: String,
            addons: List<Addon>,
        ) {
            snapshots[profileKey] = addons
        }
    }

    private class FakeAccount(
        profileKey: String,
        profileId: Int,
    ) : AccountRepository {
        private val _state =
            MutableStateFlow(
                AccountState(
                    isSignedIn = true,
                    requiresProfile = true,
                    activeProfile = BackendProfile(id = profileKey, name = "Profile", profileId = profileId),
                ),
            )
        override val state: StateFlow<AccountState> = _state.asStateFlow()

        override val signInMethods: List<SignInMethod> = emptyList()

        override suspend fun signIn(request: SignInRequest) = Unit

        override suspend fun restoreSession(): Boolean = true

        override suspend fun signOut() = Unit

        override suspend fun loadProfiles() = Unit

        override suspend fun selectProfile(profile: BackendProfile) = Unit

        fun setProfile(
            profileKey: String,
            profileId: Int,
        ) {
            _state.value =
                _state.value.copy(
                    activeProfile = BackendProfile(id = profileKey, name = "Profile", profileId = profileId),
                )
        }
    }
}
