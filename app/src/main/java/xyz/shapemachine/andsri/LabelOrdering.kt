package xyz.shapemachine.andsri

import java.text.Collator

object LabelOrdering {
    fun compare(first: String, second: String, collator: Collator = Collator.getInstance()): Int =
        collator.compare(first, second)

    fun sort(labels: List<String>, collator: Collator = Collator.getInstance()): List<String> =
        labels.sortedWith { first, second -> compare(first, second, collator) }
}
