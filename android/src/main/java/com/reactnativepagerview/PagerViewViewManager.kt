package com.reactnativepagerview

import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.facebook.infer.annotation.Assertions
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.common.MapBuilder
import com.facebook.react.uimanager.PixelUtil
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerModule
import com.facebook.react.uimanager.ViewGroupManager
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.uimanager.events.EventDispatcher
import com.reactnativepagerview.event.PageScrollEvent
import com.reactnativepagerview.event.PageScrollStateChangedEvent
import com.reactnativepagerview.event.PageSelectedEvent


class PagerViewViewManager : ViewGroupManager<NestedScrollableHost>() {
  private lateinit var eventDispatcher: EventDispatcher

  override fun getName(): String {
    return REACT_CLASS
  }

  override fun createViewInstance(reactContext: ThemedReactContext): NestedScrollableHost {
    val host = NestedScrollableHost(reactContext)
    val vp = ViewPager2(reactContext)
    val adapter = FragmentAdapter((reactContext.currentActivity as FragmentActivity?)!!)
    vp.adapter = adapter
    //https://github.com/callstack/react-native-viewpager/issues/183
    vp.isSaveEnabled = false
    eventDispatcher = reactContext.getNativeModule(UIManagerModule::class.java)!!.eventDispatcher
    vp.registerOnPageChangeCallback(object : OnPageChangeCallback() {
      override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        eventDispatcher.dispatchEvent(
                PageScrollEvent(host.id, position, positionOffset))
      }

      override fun onPageSelected(position: Int) {
        eventDispatcher.dispatchEvent(
                PageSelectedEvent(host.id, position))
      }

      override fun onPageScrollStateChanged(state: Int) {
        val pageScrollState: String = when (state) {
          ViewPager2.SCROLL_STATE_IDLE -> "idle"
          ViewPager2.SCROLL_STATE_DRAGGING -> "dragging"
          ViewPager2.SCROLL_STATE_SETTLING -> "settling"
          else -> throw IllegalStateException("Unsupported pageScrollState")
        }
        eventDispatcher.dispatchEvent(
                PageScrollStateChangedEvent(host.id, pageScrollState))
      }
    })
    host.addView(vp)
    return host
  }

  private fun getViewPager(view: NestedScrollableHost) = view.getChildAt(0) as ViewPager2

  private fun setCurrentItem(view: ViewPager2, selectedTab: Int, scrollSmooth: Boolean) {
    view.post { updateLayoutView(view) }
    view.setCurrentItem(selectedTab, scrollSmooth)
  }

  override fun addView(host: NestedScrollableHost, child: View, index: Int) {
    val parent = getViewPager(host)
    val adapter = parent.adapter as FragmentAdapter
    adapter.addReactView(child, index)
    postNewChanges(parent)
  }

  override fun getChildCount(parent: NestedScrollableHost): Int {
    val view = getViewPager(parent)
    return (view.adapter as FragmentAdapter).getReactChildCount()
  }

  override fun getChildAt(parent: NestedScrollableHost, index: Int): View {
    val view = getViewPager(parent)
    return (view.adapter as FragmentAdapter).getReactChildAt(index)
  }

  override fun removeViewAt(parent: NestedScrollableHost, index: Int) {
    val view = getViewPager(parent)
    val adapter = view.adapter as FragmentAdapter
    adapter.removeReactViewAt(index)
    postNewChanges(view)
  }

  override fun needsCustomLayoutForChildren(): Boolean {
    return true
  }

  @ReactProp(name = "count")
  fun setCount(host: NestedScrollableHost, count: Int) {
    val view = getViewPager(host)
    (view.adapter as FragmentAdapter).setCount(count)
  }

  @ReactProp(name = "scrollEnabled", defaultBoolean = true)
  fun setScrollEnabled(host: NestedScrollableHost, value: Boolean) {
    val view = getViewPager(host)
    view.isUserInputEnabled = value
  }

  @ReactProp(name = "orientation")
  fun setOrientation(host: NestedScrollableHost, value: String) {
    val view = getViewPager(host)
    view.orientation = if (value == "vertical") ViewPager2.ORIENTATION_VERTICAL else ViewPager2.ORIENTATION_HORIZONTAL
  }

  @ReactProp(name = "offscreenPageLimit", defaultInt = ViewPager2.OFFSCREEN_PAGE_LIMIT_DEFAULT)
  operator fun set(host: NestedScrollableHost, value: Int) {
    val view = getViewPager(host)
    view.offscreenPageLimit = value
  }

  @ReactProp(name = "offset")
  fun setOffset(host: NestedScrollableHost, offset: Int) {
    val view = getViewPager(host)
    (view.adapter as FragmentAdapter).setOffset(offset)
  }

  @ReactProp(name = "overScrollMode")
  fun setOverScrollMode(host: NestedScrollableHost, value: String) {
    val view = getViewPager(host)
    val child = view.getChildAt(0)
    when (value) {
      "never" -> {
        child.overScrollMode = ViewPager2.OVER_SCROLL_NEVER
      }
      "always" -> {
        child.overScrollMode = ViewPager2.OVER_SCROLL_ALWAYS
      }
      else -> {
        child.overScrollMode = ViewPager2.OVER_SCROLL_IF_CONTENT_SCROLLS
      }
    }
  }

  override fun onAfterUpdateTransaction(host: NestedScrollableHost) {
    super.onAfterUpdateTransaction(host)
    val view = getViewPager(host)
    if ((view.adapter as FragmentAdapter).notifyAboutChanges()) {
      view.post { updateLayoutView(view) }
    }
  }

  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Map<String, String>> {
    return MapBuilder.of(
            PageScrollEvent.EVENT_NAME, MapBuilder.of("registrationName", "onPageScroll"),
            PageScrollStateChangedEvent.EVENT_NAME, MapBuilder.of("registrationName", "onPageScrollStateChanged"),
            PageSelectedEvent.EVENT_NAME, MapBuilder.of("registrationName", "onPageSelected"))
  }

  override fun getCommandsMap(): Map<String, Int>? {
    return MapBuilder.of(
            "setPage",
            COMMAND_SET_PAGE,
            "setPageWithoutAnimation",
            COMMAND_SET_PAGE_WITHOUT_ANIMATION,
            "setScrollEnabled",
            COMMAND_SET_SCROLL_ENABLED)
  }

  override fun receiveCommand(root: NestedScrollableHost, commandId: Int, args: ReadableArray?) {
    super.receiveCommand(root, commandId, args)
    val view = getViewPager(root)
    Assertions.assertNotNull(view)
    Assertions.assertNotNull(args)
    val childCount = view.adapter?.itemCount

    when (commandId) {
      COMMAND_SET_PAGE, COMMAND_SET_PAGE_WITHOUT_ANIMATION -> {
        val pageIndex = args!!.getInt(0)
        val canScroll = childCount != null && childCount > 0 && pageIndex >= 0 && pageIndex < childCount
        if (canScroll) {
          val scrollWithAnimation = commandId == COMMAND_SET_PAGE
          setCurrentItem(view, pageIndex, scrollWithAnimation)
          eventDispatcher.dispatchEvent(PageSelectedEvent(root.id, pageIndex))
        }
      }
      COMMAND_SET_SCROLL_ENABLED -> {
        view.isUserInputEnabled = args!!.getBoolean(0)
      }
      else -> throw IllegalArgumentException(String.format(
              "Unsupported command %d received by %s.",
              commandId,
              javaClass.simpleName))
    }
  }

  @ReactProp(name = "pageMargin", defaultFloat = 0F)
  fun setPageMargin(host: NestedScrollableHost, margin: Float) {
    val pager = getViewPager(host)
    val pageMargin = PixelUtil.toPixelFromDIP(margin).toInt()
    /**
     * Don't use MarginPageTransformer to be able to support negative margins
     */
    pager.setPageTransformer { page, position ->
      val offset = pageMargin * position
      if (pager.orientation == ViewPager2.ORIENTATION_HORIZONTAL) {
        val isRTL = pager.layoutDirection == View.LAYOUT_DIRECTION_RTL
        page.translationX = if (isRTL) -offset else offset
      } else {
        page.translationY = offset
      }
    }
  }

  private fun postNewChanges(view: ViewPager2) {
    view.post {
      if ((view.adapter as FragmentAdapter).notifyAboutChanges()) {
        updateLayoutView(view)
      }
    }
  }

  /**
   * Helper to trigger ViewPager2 to update.
   */
  private fun updateLayoutView(view: View) {
    view.measure(
            View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(view.height, View.MeasureSpec.EXACTLY))
    view.layout(view.left, view.top, view.right, view.bottom)
  }

  companion object {
    private const val REACT_CLASS = "RNCViewPager"
    private const val COMMAND_SET_PAGE = 1
    private const val COMMAND_SET_PAGE_WITHOUT_ANIMATION = 2
    private const val COMMAND_SET_SCROLL_ENABLED = 3
  }
}
