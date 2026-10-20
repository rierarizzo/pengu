package com.misgus.pengu.ui.screens

import com.misgus.pengu.app.shared.generated.resources.Res
import com.misgus.pengu.app.shared.generated.resources.settings_addons_already_installed
import com.misgus.pengu.app.shared.generated.resources.settings_addons_unreachable
import com.misgus.pengu.core.model.Addon
import com.misgus.pengu.core.model.AddonManifest
import com.misgus.pengu.core.repository.AddonInstallException
import com.misgus.pengu.core.repository.AddonInstallFailure
import com.misgus.pengu.ui.support.FakeAccountRepository
import com.misgus.pengu.ui.support.FakeAddonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AddonsSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun addon(
        id: String,
        enabled: Boolean = true,
    ) = Addon(id = id, url = "https://$id.example/manifest.json", name = id, enabled = enabled)

    private fun viewModel(repository: FakeAddonRepository): AddonsSettingsViewModel =
        AddonsSettingsViewModel(FakeAccountRepository(), repository)

    @Test
    fun loadsEveryInstalledAddonIncludingDisabledOnes() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    FakeAddonRepository(listOf(addon("a1"), addon("a2", enabled = false))),
                )
            advanceUntilIdle()

            val ids =
                viewModel.state.value.installed
                    .map(Addon::id)
            assertEquals(listOf("a1", "a2"), ids)
            assertTrue(viewModel.state.value.hasLoaded)
        }

    @Test
    fun loadsTheManifestThatFeedsTheRowLogoAndDescription() =
        runTest(dispatcher) {
            val addon = addon("a1")
            val manifest =
                AddonManifest(
                    id = "a1",
                    name = "A1",
                    version = "1.0.0",
                    description = "A great addon",
                    logo = "https://a1.example/logo.png",
                )
            val viewModel =
                viewModel(
                    FakeAddonRepository(listOf(addon), manifests = mapOf(addon.url to manifest)),
                )
            advanceUntilIdle()

            assertEquals(manifest, viewModel.state.value.manifests["a1"])
        }

    @Test
    fun installsAndClearsTheField() =
        runTest(dispatcher) {
            val repository = FakeAddonRepository(listOf(addon("a1")))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onUrlChange("  https://new.example/manifest.json  ")
            viewModel.install()
            advanceUntilIdle()

            assertEquals("", viewModel.state.value.url)
            val ids =
                viewModel.state.value.installed
                    .map(Addon::id)
            assertEquals(listOf("a1", "addon-2"), ids)
            assertTrue(viewModel.state.value.installSucceeded)
        }

    @Test
    fun keepsTheFieldAndExplainsAFailedInstall() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    FakeAddonRepository(
                        installError = AddonInstallException(AddonInstallFailure.MANIFEST_UNREACHABLE),
                    ),
                )
            advanceUntilIdle()

            viewModel.onUrlChange("https://bad.example/manifest.json")
            viewModel.install()
            advanceUntilIdle()

            assertEquals("https://bad.example/manifest.json", viewModel.state.value.url)
            assertEquals(Res.string.settings_addons_unreachable, viewModel.state.value.error)
        }

    @Test
    fun reportsAnAlreadyInstalledAddon() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    FakeAddonRepository(
                        installError = AddonInstallException(AddonInstallFailure.ALREADY_INSTALLED),
                    ),
                )
            advanceUntilIdle()

            viewModel.onUrlChange("https://a1.example/manifest.json")
            viewModel.install()
            advanceUntilIdle()

            assertEquals(Res.string.settings_addons_already_installed, viewModel.state.value.error)
        }

    @Test
    fun togglesRemovesAndReordersTheList() =
        runTest(dispatcher) {
            val repository = FakeAddonRepository(listOf(addon("a1"), addon("a2"), addon("a3")))
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.setEnabled(addon("a2"), false)
            advanceUntilIdle()
            val disabled =
                viewModel.state.value.installed
                    .first { it.id == "a2" }
                    .enabled
            assertFalse(disabled)

            viewModel.move(addon("a3"), -1)
            advanceUntilIdle()
            val moved =
                viewModel.state.value.installed
                    .map(Addon::id)
            assertEquals(listOf("a1", "a3", "a2"), moved)

            viewModel.remove(addon("a1"))
            advanceUntilIdle()
            val remaining =
                viewModel.state.value.installed
                    .map(Addon::id)
            assertEquals(listOf("a3", "a2"), remaining)
        }
}
