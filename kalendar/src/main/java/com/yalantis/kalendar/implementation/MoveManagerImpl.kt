package com.yalantis.kalendar.implementation

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import com.yalantis.kalendar.*
import com.yalantis.kalendar.interfaces.MoveManager
import com.yalantis.kalendar.interfaces.ViewProvider

class MoveManagerImpl(private val viewProvider: ViewProvider) : MoveManager {

    override var isInAction = false

    override var isCollapsed = false

    private var startPoint = EMPTY_FLOAT

    private val finishListener = object : Animator.AnimatorListener {
        override fun onAnimationRepeat(animation: Animator?) {}
        override fun onAnimationCancel(animation: Animator?) {}
        override fun onAnimationStart(animation: Animator?) {
            isInAction = true
        }

        override fun onAnimationEnd(animation: Animator?) {
            isInAction = false
            isCollapsed = isCollapsed.not()
            viewProvider.moveStateChanged(isCollapsed, selectedWeek)
            animation?.removeAllListeners()
        }
    }

    private val topLimit = viewProvider.getTopLimit()

    private val dragHeight = viewProvider.getDragHeight()

    private val minHeight = viewProvider.viewMinHeight()

    private val viewTop = viewProvider.getViewTop()

    private var weekCount = EMPTY_INT

    private var bottomLimit = viewProvider.getBottomLimit()

    private var totalHeight = bottomLimit - viewTop

    private val weekHeight = viewProvider.getWeekHeight()

    private var selectedWeek = EMPTY_INT

    private val defaultPositions = ArrayList<Float>()

    override fun onTouch(event: MotionEvent): Boolean {
        return when (event.action) {

            MotionEvent.ACTION_UP -> {
                if (isNeedCollapse(event.y)) {
                    collapse()
                } else {
                    expand()
                }
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                isInAction = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                calculateOffsets(event.y)
                true
            }

            MotionEvent.ACTION_DOWN -> {
                isInAction = true
                startPoint = event.y
                weekCount = viewProvider.getWeekCount()
                return true
            }
            else -> false
        }
    }

    override fun setCurrentMaxHeight(totHeight: Int) {
        bottomLimit = totHeight
        totalHeight = bottomLimit - topLimit
    }

    override fun expand() {
        val anim = ValueAnimator
            .ofFloat(viewProvider.getDragTop(), bottomLimit + dragHeight.toFloat())
            .setDuration(KALENDAR_SPEED)
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            calculateOffsets(it.animatedValue as Float)
        }
        anim.addListener(finishListener)
        anim.start()
    }

    override fun collapse() {
        val anim = ValueAnimator.ofFloat(viewProvider.getDragTop(), topLimit.toFloat())
            .setDuration(KALENDAR_SPEED)
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            calculateOffsets(it.animatedValue as Float)
        }
        anim.addListener(finishListener)
        anim.start()
    }

    /**
     *  Method calculate offsets for every view on each touch position
     */

    private fun calculateOffsets(touchY: Float) {
        val newHeight = (totalHeight * (touchY / totalHeight)) + dragHeight

        if (touchY > topLimit) {

            viewProvider.setViewHeight(newHeight.toInt())
            viewProvider.setDragTop(touchY)

            for (week in 0 until weekCount) {
                if (week < selectedWeek) {
                    moveWeek(week, viewProvider.getWeekBottom(week), touchY - weekHeight, defaultPositions[week])
                } else {
                    moveWeek(week, viewProvider.getWeekBottom(week), touchY, defaultPositions[week])
                }
            }
            controlAboveSelected(touchY)
            controlBelowSelected(touchY)
        } else {
            viewProvider.moveWeek(selectedWeek, topLimit.toFloat())
            viewProvider.setViewHeight(minHeight + dragHeight)
            viewProvider.setDragTop(minHeight.toFloat())
            controlAboveSelected(touchY, overScroll = true)
            controlBelowSelected(touchY)
        }
    }

    /**
     *  Method calculate current alpha depends on offset from default week bottom
     */

    private fun applyAlpha(week: Int, touchY: Float, defaultBottom: Float) {
        if (week != selectedWeek) {
            val alpha = (1 - (Math.abs(defaultBottom - touchY) / (weekHeight / HIDE_MULTIPLIER)))
            if (alpha in ALPHA_RANGE) {
                viewProvider.applyAlpha(week, alpha)
            }
        }
    }

    /**
     *  Method prevents weeks above selected week from being hided/shown when shouldn't
     */

    private fun controlAboveSelected(touchY: Float, overScroll: Boolean = false) {
        val weeksAbove = weekCount - (weekCount - selectedWeek)
        var weekTop: Float
        var weekBottom: Float

        for (week in 0 until weeksAbove) {
            weekTop = defaultPositions[week] - weekHeight
            weekBottom = defaultPositions[week]

            when {
                overScroll -> viewProvider.applyAlpha(week, ALPHA_INVISIBLE)

                // makes sure that week hide
                touchY > weekTop && touchY < weekBottom -> {
                    viewProvider.applyAlpha(week, ALPHA_INVISIBLE)
                }
                // makes sure that week show
                touchY > weekBottom + weekHeight -> {
                    viewProvider.applyAlpha(week, ALPHA_VISIBLE)
                }
            }
        }
    }

    /**
     *  Method prevents weeks below selected week from being hided/shown when shouldn't
     */

    private fun controlBelowSelected(touchY: Float) {
        for (week in selectedWeek + 1 until weekCount) {
            when {
                // touch below default bottom position
                touchY > defaultPositions[week] -> {
                    viewProvider.applyAlpha(week, ALPHA_VISIBLE)
                }
                // touch above default top position
                touchY < defaultPositions[week] - weekHeight -> {
                    viewProvider.applyAlpha(week, ALPHA_INVISIBLE)
                }
            }
        }
    }

    /**
     *  Method move week depends on current and touch positions
     */

    private fun moveWeek(which: Int, weekBottom: Float, touchY: Float, defaultPosition: Float) {
        when {
            // check if week need to be placed on default position
            weekBottom != defaultPosition && touchY > defaultPosition -> {
                viewProvider.moveWeek(which, defaultPosition)
            }
            weekBottom > touchY -> {
                viewProvider.moveWeek(which, touchY)
                applyAlpha(which, touchY, defaultPosition)
            }
            weekBottom <= touchY && touchY < defaultPosition -> {
                viewProvider.moveWeek(which, touchY)
                applyAlpha(which, touchY, defaultPosition)
            }
        }
    }

    /**
     *  True when finger moved more than one week height from touch start point
     *  False otherwise
     */

    private fun isNeedCollapse(y: Float) = Math.abs(startPoint - y) > weekHeight && isCollapsed.not()

    override fun selectWeek(selectedWeek: Int) {
        this.selectedWeek = selectedWeek
        this.weekCount = viewProvider.getWeekCount()
        refreshDefaultPositions()
    }

    private fun refreshDefaultPositions() {
        //weeks 0-5 (if displayed 6)
        for (i in 0 until weekCount) {
            defaultPositions.add(viewProvider.getWeekBottom(i))
        }
    }
}