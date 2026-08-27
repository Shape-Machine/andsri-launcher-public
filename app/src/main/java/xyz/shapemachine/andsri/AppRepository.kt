package xyz.shapemachine.andsri

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import java.text.Collator

class AppRepository(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val collator = Collator.getInstance()

    fun allApps(customLabels: Map<String, String> = emptyMap()): List<AppEntry> =
        launcherApps
            .getActivityList(null, Process.myUserHandle())
            .asSequence()
            .map { activity ->
                val component = activity.componentName
                AppEntry(
                    component = component,
                    label = customLabels[component.flattenToString()] ?: activity.label.toString(),
                )
            }
            .sortedWith { first, second -> LabelOrdering.compare(first.label, second.label, collator) }
            .toList()

    fun visibleApps(hiddenComponents: Set<String>, customLabels: Map<String, String> = emptyMap()): List<AppEntry> =
        allApps(customLabels).filterNot { it.component.flattenToString() in hiddenComponents }
}
