package com.flowpilot.automation.data

object ExampleAutomations {
    fun all(): List<Automation> = listOf(
        openAndTapWhenText(),
        openAndTapImage(),
        morningUrl(),
        lowBatteryAlert(),
        autoReplyHelper(),
        clipboardToNote(),
        waitNotificationThenTap(),
        wifiConnectedToast(),
        bootWelcome(),
        repeatScroll(),
        ifElementExists(),
        httpHealthCheck()
    )

    /** Gallery entries (may already exist as saved flows). */
    fun gallery(): List<Automation> = all().map {
        it.copy(id = "template-${it.id.removePrefix("example-")}", enabled = it.id != "example-auto-reply")
    }

    private fun openAndTapWhenText() = Automation(
        id = "example-open-tap-text",
        name = "Open app & tap when text appears",
        description = "Opens an app, waits for a label, then taps it. Pick the app first.",
        trigger = Trigger.Manual,
        steps = listOf(
            Block(type = BlockType.OPEN_APP, params = mapOf("package" to "com.android.settings")),
            Block(type = BlockType.WAIT_FOR_ELEMENT, params = mapOf("text" to "Search", "timeoutMs" to "8000", "partial" to "true")),
            Block(type = BlockType.CLICK_BY_TEXT, params = mapOf("text" to "Search", "partial" to "true")),
            Block(type = BlockType.TOAST, params = mapOf("text" to "Tapped Search"))
        )
    )

    private fun openAndTapImage() = Automation(
        id = "example-open-tap-image",
        name = "Open app & tap when image appears",
        description = "Waits for a template image, then taps it. Capture a template first.",
        trigger = Trigger.Manual,
        steps = listOf(
            Block(type = BlockType.OPEN_APP, params = mapOf("package" to "com.android.settings")),
            Block(type = BlockType.WAIT, params = mapOf("ms" to "2000")),
            Block(
                type = BlockType.FIND_IMAGE,
                params = mapOf("template" to "sample_button.png", "threshold" to "0.75", "timeoutMs" to "8000", "outX" to "foundX", "outY" to "foundY")
            ),
            Block(
                type = BlockType.IF,
                params = mapOf("op" to "neq", "left" to "{{foundX}}", "right" to "-1"),
                children = listOf(
                    Block(type = BlockType.TAP_XY, params = mapOf("x" to "{{foundX}}", "y" to "{{foundY}}")),
                    Block(type = BlockType.TOAST, params = mapOf("text" to "Tapped found image"))
                ),
                elseChildren = listOf(
                    Block(type = BlockType.TOAST, params = mapOf("text" to "Image not found — capture a template first"))
                )
            )
        )
    )

    private fun morningUrl() = Automation(
        id = "example-morning-url",
        name = "Daily URL open",
        description = "Every day at 8:00 open a webpage.",
        trigger = Trigger.Schedule(hour = 8, minute = 0),
        steps = listOf(
            Block(type = BlockType.OPEN_URL, params = mapOf("url" to "https://news.ycombinator.com")),
            Block(type = BlockType.TOAST, params = mapOf("text" to "Good morning"))
        )
    )

    private fun lowBatteryAlert() = Automation(
        id = "example-battery",
        name = "Low battery alert",
        description = "When battery drops below 20%, show a notification.",
        trigger = Trigger.Battery(belowPercent = 20),
        steps = listOf(
            Block(type = BlockType.SHOW_NOTIFICATION, params = mapOf("title" to "Low battery", "text" to "Battery is {{battery}}%")),
            Block(type = BlockType.VIBRATE, params = mapOf("ms" to "400"))
        )
    )

    private fun autoReplyHelper() = Automation(
        id = "example-auto-reply",
        name = "Auto-reply helper (off by default)",
        description = "When a notification arrives, toast a reminder. Keep disabled until you customize it.",
        enabled = false,
        trigger = Trigger.NotificationReceived(),
        steps = listOf(
            Block(type = BlockType.TOAST, params = mapOf("text" to "Notification received — customize this flow")),
            Block(type = BlockType.LOG, params = mapOf("message" to "Auto-reply helper fired (edit me)"))
        )
    )

    private fun clipboardToNote() = Automation(
        id = "example-clipboard-note",
        name = "Copy clipboard to note file",
        description = "Append the current clipboard text to notes.txt.",
        trigger = Trigger.Manual,
        steps = listOf(
            Block(type = BlockType.SET_VARIABLE, params = mapOf("name" to "clip", "value" to "{{clipboard}}", "type" to "text")),
            Block(type = BlockType.WRITE_FILE, params = mapOf("path" to "notes.txt", "text" to "{{clip}}\n", "append" to "true")),
            Block(type = BlockType.TOAST, params = mapOf("text" to "Saved clipboard to notes.txt"))
        )
    )

    private fun waitNotificationThenTap() = Automation(
        id = "example-notif-tap",
        name = "Wait for notification then tap",
        description = "After a notification, wait briefly and press Back (customize the tap).",
        trigger = Trigger.NotificationReceived(),
        steps = listOf(
            Block(type = BlockType.WAIT, params = mapOf("ms" to "1500")),
            Block(type = BlockType.TOAST, params = mapOf("text" to "Notification seen — edit the next tap")),
            Block(type = BlockType.PRESS_BACK)
        )
    )

    private fun wifiConnectedToast() = Automation(
        id = "example-wifi",
        name = "Wi-Fi connected toast",
        description = "When Wi-Fi connects, show a short message.",
        trigger = Trigger.Wifi(connected = true),
        steps = listOf(
            Block(type = BlockType.TOAST, params = mapOf("text" to "Wi-Fi connected"))
        )
    )

    private fun bootWelcome() = Automation(
        id = "example-boot",
        name = "Welcome after reboot",
        description = "After the phone boots, speak a short greeting.",
        trigger = Trigger.Boot,
        steps = listOf(
            Block(type = BlockType.WAIT, params = mapOf("ms" to "5000")),
            Block(type = BlockType.TTS, params = mapOf("text" to "Welcome back. FlowPilot is ready.")),
            Block(type = BlockType.SHOW_NOTIFICATION, params = mapOf("title" to "FlowPilot", "text" to "Ready after reboot"))
        )
    )

    private fun repeatScroll() = Automation(
        id = "example-scroll",
        name = "Scroll down 5 times",
        description = "Repeat a swipe downward five times.",
        trigger = Trigger.Manual,
        steps = listOf(
            Block(
                type = BlockType.FOR,
                params = mapOf("count" to "5", "var" to "i"),
                children = listOf(
                    Block(type = BlockType.SWIPE, params = mapOf("x1" to "540", "y1" to "1400", "x2" to "540", "y2" to "500", "durationMs" to "400")),
                    Block(type = BlockType.WAIT, params = mapOf("ms" to "500"))
                )
            )
        )
    )

    private fun ifElementExists() = Automation(
        id = "example-if-element",
        name = "If element with text exists",
        description = "Checks for on-screen text and branches.",
        trigger = Trigger.Manual,
        steps = listOf(
            Block(
                type = BlockType.IF,
                params = mapOf("kind" to "element_exists", "text" to "OK", "partial" to "true"),
                children = listOf(
                    Block(type = BlockType.CLICK_BY_TEXT, params = mapOf("text" to "OK", "partial" to "true")),
                    Block(type = BlockType.TOAST, params = mapOf("text" to "Tapped OK"))
                ),
                elseChildren = listOf(
                    Block(type = BlockType.TOAST, params = mapOf("text" to "OK not on screen"))
                )
            )
        )
    )

    private fun httpHealthCheck() = Automation(
        id = "example-http",
        name = "HTTP health check",
        description = "GET a URL and toast the result length.",
        trigger = Trigger.Manual,
        steps = listOf(
            Block(type = BlockType.HTTP_REQUEST, params = mapOf("method" to "GET", "url" to "https://example.com", "out" to "httpBody")),
            Block(type = BlockType.TOAST, params = mapOf("text" to "Response stored in httpBody"))
        )
    )
}
