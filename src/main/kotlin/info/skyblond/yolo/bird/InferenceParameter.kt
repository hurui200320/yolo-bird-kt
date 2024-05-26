package info.skyblond.yolo.bird

data class InferenceParameter(
    val nmsThreshold: Double,
    val interestMap: Map<String, Float>
) {
    fun getThreshold(label: String) = interestMap[label] ?: Float.MAX_VALUE
}
