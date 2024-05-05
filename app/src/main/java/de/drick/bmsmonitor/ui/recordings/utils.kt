package de.drick.bmsmonitor.ui.recordings

import de.drick.bmsmonitor.repository.RecordEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow


@OptIn(kotlin.experimental.ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T> Iterable<T>.averageOf(selector: (T) -> Int): Int {
    var sum = 0
    var counter = 0
    for (element in this) {
        sum += selector(element)
        counter++
    }
    return sum / counter
}

@OptIn(kotlin.experimental.ExperimentalTypeInference::class)
@OverloadResolutionByLambdaReturnType
inline fun <T> Iterable<T>.averageOf(selector: (T) -> Float): Float {
    var sum = 0f
    var counter = 0
    for (element in this) {
        sum += selector(element)
        counter++
    }
    return sum / counter
}

fun formatDuration(elapsedSeconds: Long): String {
    var sec = elapsedSeconds
    // Break the elapsed seconds into hours, minutes, and seconds.
    var hours = 0L
    var minutes = 0L
    if (sec >= 3600) {
        hours = sec / 3600L
        sec -= hours * 3600L
    }
    if (sec >= 60) {
        minutes = sec / 60L
        sec -= minutes * 60L
    }
    val seconds = sec
    return buildString {
        if (hours > 0) append("$hours:")
        append("$minutes:")
        if (seconds < 10) {
            append("0")
        }
        append(seconds)
    }
}

fun List<RecordEntry>.step(timeSteps: Long) = buildList<RecordEntry> {
    var lastStep = 0L
    val buffer = mutableListOf<RecordEntry>()
    val iter = this@step.iterator()
    while (iter.hasNext()) {
        val entry = iter.next()
        val tStep = entry.time / timeSteps
        if (tStep > lastStep) {
            if (buffer.size > 0) {
                add(RecordEntry(
                    time = lastStep * timeSteps,
                    voltage = buffer.averageOf { it.voltage },
                    current = buffer.averageOf { it.current },
                    soc = buffer.averageOf { it.soc },
                    temp = buffer.averageOf { it.temp }
                ))
                buffer.clear()
            }
            lastStep = tStep
        }
        buffer.add(entry)
    }
    if (buffer.size > 0) {
        add(RecordEntry(
            time = lastStep * timeSteps,
            voltage = buffer.averageOf { it.voltage },
            current = buffer.averageOf { it.current },
            soc = buffer.averageOf { it.soc },
            temp = buffer.averageOf { it.temp }
        ))
    }
}

fun Flow<RecordEntry>.step(timeSteps: Long) = flow {
    var lastStep = 0L
    val buffer = mutableListOf<RecordEntry>()
    collect { entry ->
        val tStep = entry.time / timeSteps
        if (tStep > lastStep) {
            if (buffer.size > 0) {
                emit(RecordEntry(
                    time = lastStep * timeSteps,
                    voltage = buffer.averageOf { it.voltage },
                    current = buffer.averageOf { it.current },
                    soc = buffer.averageOf { it.soc },
                    temp = buffer.averageOf { it.temp }
                ))
                buffer.clear()
            }
            lastStep = tStep
        }
        buffer.add(entry)
    }
    if (buffer.size > 0) {
        emit(RecordEntry(
            time = lastStep * timeSteps,
            voltage = buffer.averageOf { it.voltage },
            current = buffer.averageOf { it.current },
            soc = buffer.averageOf { it.soc },
            temp = buffer.averageOf { it.temp }
        ))
    }
}
