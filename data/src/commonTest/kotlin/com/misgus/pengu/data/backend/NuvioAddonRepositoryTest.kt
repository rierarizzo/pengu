package com.misgus.pengu.data.backend

import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.BackendProfile
import com.misgus.pengu.core.repository.AccountRepository
import com.misgus.pengu.core.repository.AccountState
import com.misgus.pengu.core.repository.AddonInstallException
import com.misgus.pengu.core.repository.AddonInstallFailure
import com.misgus.pengu.core.repository.SignInMethod
import com.misgus.pengu.core.repository.SignInRequest
import com.misgus.pengu.data.store.AddonSnapshotStore
import io.ktor.client.HttpClient
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
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
                                """[{"id":"a1","url":"https://addon/manifest.json","name":"Addon","enabled":true,"sort_order":0,"profile_id":1},{"id":"a2","url":"https://off/manifest.json","name":"Off","enabled":false,"sort_order":1,"profile_id":1}]""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        }
                        else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                    }
                }
            val stack = nuvioTestStack(engine)
            val account = FakeAccount(profileKey = "p1", profileId = 1)
            val repository = NuvioAddonRepository(stack.rest, account, stack.auth, HttpClient(engine))
            stack.auth.signIn("me@example.com", "secret")

            val first = repository.all()
            val second = repository.all()

            assertEquals(1, addonCalls)
            assertEquals(1, first.size)
            assertEquals(first, second)
            // Management sees the disabled addon too; the content path does not.
            assertEquals(listOf("a1", "a2"), repository.installed().map(Addon::id))
            assertEquals(1, addonCalls)

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
            val repository =
                NuvioAddonRepository(
                    stack.rest,
                    account,
                    stack.auth,
                    HttpClient(engine),
                    store,
                    backgroundScope,
                )
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

    @Test
    fun installsAnAddonAfterReadingItsManifest() =
        runTest {
            var posted = false
            val rows =
                mutableListOf(
                    """{"id":"a1","url":"https://old.example/manifest.json","name":"Old","enabled":true,"sort_order":0,"profile_id":1}""",
                )
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
                        path.endsWith("/manifest.json") ->
                            respond(
                                """{"id":"new","name":"New Addon","version":"1.0.0"}""",
                                HttpStatusCode.OK,
                                jsonHeaders,
                            )
                        path.contains("/rest/v1/addons") ->
                            when (request.method.value) {
                                "POST" -> {
                                    posted = true
                                    rows +=
                                        """{"id":"a2","url":"https://new.example/manifest.json","name":"New Addon","enabled":true,"sort_order":1,"profile_id":1}"""
                                    respond("", HttpStatusCode.Created, jsonHeaders)
                                }
                                else -> respond("[${rows.joinToString(",")}]", HttpStatusCode.OK, jsonHeaders)
                            }
                        else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                    }
                }
            val stack = nuvioTestStack(engine)
            val account = FakeAccount(profileKey = "p1", profileId = 1)
            val repository = NuvioAddonRepository(stack.rest, account, stack.auth, HttpClient(engine))
            stack.auth.signIn("me@example.com", "secret")

            val installed = repository.install("https://new.example/manifest.json")

            assertTrue(posted)
            assertEquals("a2", installed.id)
            assertEquals("New Addon", installed.name)
            assertEquals(listOf("a1", "a2"), repository.installed().map(Addon::id))
            // The settings page reads the same manifest for the logo and copy.
            assertEquals("New Addon", repository.manifest("https://new.example")?.name)
        }

    @Test
    fun scopesWriteFiltersToTheProfileAndReReadsTheList() =
        runTest {
            val calls = mutableListOf<String>()
            val rows =
                mutableListOf(
                    """{"id":"a1","url":"https://one.example/manifest.json","name":"One","enabled":true,"sort_order":0,"profile_id":1}""",
                    """{"id":"a2","url":"https://two.example/manifest.json","name":"Two","enabled":true,"sort_order":1,"profile_id":1}""",
                )
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
                        path.contains("/rest/v1/addons") ->
                            when (request.method.value) {
                                "PATCH" -> {
                                    calls += "PATCH ${request.url.encodedQuery}"
                                    respond("", HttpStatusCode.NoContent, jsonHeaders)
                                }
                                "DELETE" -> {
                                    calls += "DELETE ${request.url.encodedQuery}"
                                    rows.removeAll { it.contains("\"id\":\"a1\"") }
                                    respond("", HttpStatusCode.NoContent, jsonHeaders)
                                }
                                else -> respond("[${rows.joinToString(",")}]", HttpStatusCode.OK, jsonHeaders)
                            }
                        else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
                    }
                }
            val stack = nuvioTestStack(engine)
            val account = FakeAccount(profileKey = "p1", profileId = 1)
            val repository = NuvioAddonRepository(stack.rest, account, stack.auth, HttpClient(engine))
            stack.auth.signIn("me@example.com", "secret")

            repository.setEnabled("a1", false)
            repository.remove("a1")
            repository.reorder(listOf("a2"))

            assertEquals(
                listOf(
                    "PATCH id=eq.a1&profile_id=eq.1",
                    "DELETE id=eq.a1&profile_id=eq.1",
                    "PATCH id=eq.a2&profile_id=eq.1",
                ),
                calls,
            )
            assertEquals(listOf("a2"), repository.installed().map(Addon::id))
        }

    @Test
    fun rejectsANonHttpUrlBeforeTouchingTheBackend() =
        runTest {
            val engine = MockEngine { respond("{}", HttpStatusCode.NotFound, jsonHeaders) }
            val stack = nuvioTestStack(engine)
            val account = FakeAccount(profileKey = "p1", profileId = 1)
            val repository = NuvioAddonRepository(stack.rest, account, stack.auth, HttpClient(engine))

            val failure =
                assertFailsWith<AddonInstallException> {
                    repository.install("addon.example/manifest.json")
                }

            assertEquals(AddonInstallFailure.INVALID_URL, failure.reason)
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
