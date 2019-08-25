package algorithm

class AdjacencyMap {

    private var firstIndex = 0
    private val segments = arrayListOf<Segment?>()
    private val mapStartToEnd = hashMapOf<Point, ArrayList<Int>>()
    private val mapEndToStart = hashMapOf<Point, ArrayList<Int>>()

    fun addSegment(start: Point, end: Point) {
        if (start == end) return

        val i = segments.size
        segments.add(Segment(start, end))

        if (mapStartToEnd.containsKey(start)) mapStartToEnd[start]!!.add(i)
        else mapStartToEnd[start] = arrayListOf(i)

        if (mapEndToStart.containsKey(end)) mapEndToStart[end]!!.add(i)
        else mapEndToStart[end] = arrayListOf(i)
    }

    fun startFromEnd(end: Point): Point? {
        var start: Point? = null

        if (mapEndToStart.containsKey(end)) {
            val entry = mapEndToStart[end]
            val i = entry!![0]
            start = segments[i]!!.start
            removeSegmentAt(i)
        }

        return start
    }

    fun endFromStart(start: Point): Point? {

        var end: Point? = null

        if (mapStartToEnd.containsKey(start)) {
            val entry = mapStartToEnd[start]
            val i = entry!![0]
            end = segments[i]!!.end
            removeSegmentAt(i)
        }

        return end
    }

    fun firstSegment(): Segment? {
        var segment: Segment? = null

        for (i in firstIndex until segments.size) {
            segment = segments[i]
            if (segment != null) {
                removeSegmentAt(i)
                firstIndex = i
                break
            }
        }

        return segment
    }

    private fun removeSegmentAt(i: Int) {
        val segment = segments[i]

        val start = segment!!.start
        val end = segment.end

        var entry = mapStartToEnd[start]
        entry!!.remove(i)
        if (entry.size == 0) mapStartToEnd.remove(start)

        entry = mapEndToStart[end]
        entry!!.remove(i)
        if (entry.size == 0) mapEndToStart.remove(end)

        segments[i] = null
    }
}