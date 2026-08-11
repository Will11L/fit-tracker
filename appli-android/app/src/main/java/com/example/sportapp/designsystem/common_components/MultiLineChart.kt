package com.example.sportapp.designsystem.common_components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle.Companion.Fill
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.sportapp.feature.exercises.ui.components.exerciseScreen.WorkoutStatEntry
import com.example.sportapp.designsystem.theme.appColors
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberEnd
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer.Line
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer.LineFill
import com.patrykandpatrick.vico.core.common.Fill
import com.patrykandpatrick.vico.core.common.data.ExtraStore


@Composable
fun MultiLineChart(
    data: List<WorkoutStatEntry>,
    selectedMetrics: List<String>,
    colorMap: Map<String, Color>
) {
    val modelProducer = remember { CartesianChartModelProducer() }

    val height = 200.dp
    val borderColor = appColors.primaryAction

    if (selectedMetrics.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.bgRecessed)
                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No data selected",
                color = borderColor
            )
        }
    } else {
        LaunchedEffect(data, selectedMetrics) {
            modelProducer.runTransaction {
                lineSeries {
                    selectedMetrics.forEach { metric ->
                        println ("Adding series for metric: $metric with data size: $data")
                        when (metric) {
                            "Weight" -> series(data.map { it.weight.toFloat() })
                            "Sets" -> series(data.map { it.sets.toFloat() })
                            "Volume" -> series(data.map { it.volume })
                        }
                    }
                }
            }
        }

        val specs = selectedMetrics.map { metric ->
            Line(
                fill = LineFill.single(fill(colorMap[metric] ?: appColors.textTertiary)),
                stroke = LineCartesianLayer.LineStroke.Continuous(thicknessDp = 2f),
                pointConnector = LineCartesianLayer.PointConnector.cubic(curvature = 0.75f),
            )
        }

        val lineProvider = LineCartesianLayer.LineProvider.series(specs)

        val dashedGuideline = rememberLineComponent(
            fill = Fill(appColors.textTertiary.toArgb()),
            thickness = 0.1.dp, // ou réduire à 0.5.dp pour lignes très fines
        )

        val spacing = { _: ExtraStore ->
            maxOf(1, data.size / 10) // Environ 10 ticks max
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(8.dp))
                .background(appColors.bgRecessed)
                //.border(0.dp, borderColor, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            CartesianChartHost(
                rememberCartesianChart(
                    rememberLineCartesianLayer(
                        lineProvider = lineProvider,
                        rangeProvider = remember {
                            CartesianLayerRangeProvider.fixed(
                                minY = 0.0,
                                // maxY = 200.0
                                minX = 0.0,
                                //maxX = (data.size - 1).toDouble()
                            )
                        }
                    ),
                    startAxis = VerticalAxis.rememberStart(
                        guideline = dashedGuideline
                    ),
                    bottomAxis = HorizontalAxis.rememberBottom(
                        guideline = null,
                        itemPlacer = remember {
                            HorizontalAxis.ItemPlacer.aligned(spacing = spacing)
                        }
                    )
                ),
                modelProducer = modelProducer,
                modifier = Modifier
                    .fillMaxSize()
            )
        }
    }
}
