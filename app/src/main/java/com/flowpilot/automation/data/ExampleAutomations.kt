package com.flowpilot.automation.data

object ExampleAutomations {
    fun all(): List<Automation> = listOf(openAndTapImage(), batteryNotify(), morningUrl())

    private fun openAndTapImage() = Automation(
        id = "example-open-tap-image",
        name = "Open app & tap when image appears",
        description = "Opens an app package, waits for a template image, then taps it. Edit package/template first.",
        trigger = Trigger.Manual,
        steps = listOf(
            Block(type = BlockType.LOG, params = mapOf("message" to "Starting open+find+tap flow")),
            Block(type = BlockType.OPEN_APP, params = mapOf("package" to "com.android.settings")),
            Block(type = BlockType.WAIT, params = mapOf("ms" to "2000")),
            Block(
                type = BlockType.FIND_IMAGE,
                params = mapOf(
                    "template" to "sample_button.png",
                    "threshold" to "0.75",
                    "timeoutMs" to "8000",
                    "outX" to "foundX",
                    "outY" to "foundY"
                )
            ),
            Block(
                type = BlockType.IF,
                params = mapOf("op" to "neq", "left" to "{{foundX}}", "right" to "-1"),
                children = listOf(
                    Block(type = BlockType.TAP_XY, params = mapOf("x" to "{{foundX}}", "y" to "{{foundY}}")),
                    Block(type = BlockType.TOAST, params = mapOf("text" to "Tapped found image"))
                ),
                elseChildren = listOf(
                    Block(type = BlockType.LOG, params = mapOf("message" to "Image not found")),
                    Block(type = BlockType.TOAST, params = mapOf("text" to "Image not found — capture a template first"))
                )
            )
        )
    )

    private fun batteryNotify() = Automation(
        id = "example-battery",
        name = "If battery < 20% notify else log",
        description = "Checks battery level via system variable and notifies or logs.",
        trigger = Trigger.Battery(belowPercent = 20),
        steps = listOf(
            Block(type = BlockType.SET_VARIABLE, params = mapOf("name" to "bat", "value" to "{{battery}}", "type" to "number")),
            Block(
                type = BlockType.IF,
                params = mapOf("op" to "lt", "left" to "{{bat}}", "right" to "20"),
                children = listOf(
                    Block(
                        type = BlockType.SHOW_NOTIFICATION,
                        params = mapOf("title" to "Low battery", "text" to "Battery is {{bat}}%")
                    )
                ),
                elseChildren = listOf(
                    Block(type = BlockType.LOG, params = mapOf("message" to "Battery OK at {{bat}}%"))
                )
            )
        )
    )

    private fun morningUrl() = Automation(
        id = "example-morning-url",
        name = "Every day at 8 AM open a URL",
        description = "Scheduled daily trigger opens a URL in the browser.",
        trigger = Trigger.Schedule(hour = 8, minute = 0),
        steps = listOf(
            Block(type = BlockType.LOG, params = mapOf("message" to "Morning schedule fired")),
            Block(type = BlockType.OPEN_URL, params = mapOf("url" to "https://news.ycombinator.com")),
            Block(type = BlockType.TOAST, params = mapOf("text" to "Good morning — URL opened"))
        )
    )
}
