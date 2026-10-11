package io.github.tukiza7_debug.vibecast.player

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks

/**
 * Pure quality selection logic: Auto clears video overrides, a specific selection
 * pins one track of its group. Unit tested on the JVM.
 */
object QualitySelectorLogic {

    data class VideoOption(
        val group: Tracks.Group,
        val trackIndex: Int,
        val label: String,
        val height: Int,
        val bitrate: Int,
    )

    fun videoOptions(tracks: Tracks): List<VideoOption> {
        val options = mutableListOf<VideoOption>()
        tracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_VIDEO) return@forEach
            for (trackIndex in 0 until group.mediaTrackGroup.length) {
                if (group.isTrackSupported(trackIndex)) {
                    val format = group.getTrackFormat(trackIndex)
                    options += VideoOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = format.height.takeIf { it > 0 }?.let { "${it}p" } ?: (format.label ?: "Track $trackIndex"),
                        height = format.height,
                        bitrate = format.bitrate,
                    )
                }
            }
        }
        return options.distinctBy { it.label }.sortedByDescending { it.height }
    }

    fun apply(
        parameters: TrackSelectionParameters.Builder,
        option: VideoOption?,
    ): TrackSelectionParameters.Builder {
        parameters.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (option != null) {
            parameters.setOverrideForType(
                TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex),
            )
        }
        return parameters
    }
}
