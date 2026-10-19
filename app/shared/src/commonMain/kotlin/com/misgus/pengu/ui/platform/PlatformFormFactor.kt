package com.misgus.pengu.ui.platform

/**
 * Whether the app runs in a desktop window.
 *
 * It picks the input idiom, not the layout: a settings picker is a popover on
 * desktop and a bottom sheet on a touch device, even when that device is a wide
 * tablet. [com.misgus.pengu.ui.theme.LocalWindowSize] answers the layout
 * question instead.
 */
expect val isDesktop: Boolean
