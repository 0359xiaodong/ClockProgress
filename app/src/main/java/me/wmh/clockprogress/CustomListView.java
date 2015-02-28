package me.wmh.clockprogress;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ListView下拉刷新和加载更多
 * </br>如果设置了OnRefreshListener接口和OnLoadMoreListener接口,并且不为null，功能开启。
 */
public class CustomListView extends ListView implements OnScrollListener {

    /**
     * 显示格式化日期模板
     */
    private final static String DATE_FORMAT_STR = "yyyy-MM-dd HH:mm";

    /**
     * 实际的padding的距离与界面上偏移距离的比例(即下拉刷新的阻尼度)
     */
    private final static int RATIO = 3;

    /**
     * 已经达到下拉距离的要求，状态为释放刷新
     */
    private final static int RELEASE_TO_REFRESH = 0;

    /**
     * 还没有达到下拉距离要求，状态为下拉刷新
     */
    private final static int PULL_TO_REFRESH = 1;

    /**
     * 正在下拉刷新中
     */
    private final static int HEADER_REFRESHING = 2;

    /**
     * 下拉刷新完成
     */
    private final static int HEADER_REFRESHING_DONE = 3;

    /**
     * <p/>0:RELEASE_TO_REFRESH;
     * <p/>1:PULL_TO_REFRESH;
     * <p/>2:HEADER_REFRESHING;
     * <p/>3:HEADER_REFRESHING_DONE;
     */
    private int mHeadState;

    /**
     * 加载更多中
     */
    private final static int FOOTER_LOADING = 1;

    /**
     * 手动加载更多完成
     */
    private final static int FOOTER_MANUAL_LOAD_DONE = 2;

    /**
     * 自动加载更多完成
     */
    private final static int FOOTER_AUTO_LOAD_DONE = 3;

    /**
     * 加载更多错误
     */
    private final static int FOOTER_LOAD_ERROR = 4;

    /**
     * <p/>1:FOOTER_LOADING;
     * <p/>2:FOOTER_MANUAL_LOAD_DONE;
     * <p/>3:FOOTER_AUTO_LOAD_DONE;
     * <p/>4:FOOTER_LOAD_ERROR;
     */
    private int mFooterState;

    private String PULL_TO_REFRESH_TXT = "";
    private String RELEASE_TO_REFRESH_TXT = "";

    private boolean isPullDownRefreshing = false;
    private boolean isPullUpLoading = false;

    /**
     * 是否可以加载更多
     */
    private boolean mCanLoadMore = false;
    /**
     * 是否可以下拉刷新
     */
    private boolean mCanRefresh = false;
    /**
     * 是否可以自动加载更多（注意，先判断是否可以加载更多，如果没有，这个flag也没有意义）
     */
    private boolean mIsAutoLoadMore = true;
    /**
     * 下拉刷新后是否显示第一条Item
     */
    private boolean mIsMoveToFirstItemAfterRefresh = true;
    private boolean mScrollToLoadMore = true;

    private LayoutInflater mInflater;
    private LinearLayout mHeaderView;
    private TextView mTipsTextView;
    private TextView mLastUpdatedTextView;
    private ClockProgress cProgress;
    private View mFooterView;
    private ProgressBar mFooterLoadProgressBar;
    private TextView mFooterLoadTipsTextView;

    /**
     * 用于保证startY的值在一个完整的touch事件中只被记录一次
     */
    private boolean mIsRecored;

    private int mHeadViewWidth;
    private int mHeadViewHeight;

    private int mStartY;
    private boolean mIsBack;

    private int mFirstItemIndex;
    private int mLastItemIndex;
    private int mCount;
    /**
     * 是否足够数量充满屏幕
     */
    private boolean mEnoughCount;
    boolean enoughCount = false;
    private OnRefreshListener mRefreshListener;
    private OnLoadMoreListener mLoadMoreListener;

    public CustomListView(Context pContext, AttributeSet pAttrs) {
        super(pContext, pAttrs);
        init(pContext);
    }

    public CustomListView(Context pContext) {
        super(pContext);
        init(pContext);
    }

    public CustomListView(Context pContext, AttributeSet pAttrs, int pDefStyle) {
        super(pContext, pAttrs, pDefStyle);
        init(pContext);
    }

    /**
     * 初始化操作
     *
     * @param pContext
     */
    private void init(Context pContext) {
        setCacheColorHint(Color.parseColor("#00000000"));
        mInflater = LayoutInflater.from(pContext);
        addHeadView();
        setOnScrollListener(this);
    }

    /**
     * 添加下拉刷新的HeadView
     */
    private void addHeadView() {
        mHeaderView = (LinearLayout) mInflater.inflate(R.layout.list_header_view,null);

        cProgress = (ClockProgress) mHeaderView.findViewById(R.id.clock_progress);
        mTipsTextView = (TextView) mHeaderView.findViewById(R.id.head_tipsTextView);
        mLastUpdatedTextView = (TextView) mHeaderView.findViewById(R.id.head_lastUpdatedTextView);

        //首次创建时测量headview的高度之后设置padding值隐藏掉headview
        measureView(mHeaderView);
        mHeadViewHeight = mHeaderView.getMeasuredHeight();
        mHeadViewWidth = mHeaderView.getMeasuredWidth();
        mHeaderView.setPadding(0, -1 * mHeadViewHeight, 0, 0);
        mHeaderView.invalidate();

        addHeaderView(mHeaderView, null, false);

        mHeadState = HEADER_REFRESHING_DONE;
    }

    /**
     * 添加加载更多FootView
     */
    private void addFooterView() {
        mFooterView = mInflater.inflate(R.layout.list_footer_view, null);
        mFooterView.setVisibility(View.VISIBLE);
        mFooterLoadProgressBar = (ProgressBar) mFooterView.findViewById(R.id.pull_to_refresh_progress);
        mFooterLoadTipsTextView = (TextView) mFooterView.findViewById(R.id.load_more);

        mFooterView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mCanLoadMore) {
                    if (mCanRefresh) {
                        // 当可以下拉刷新时，如果FootView没有正在加载，并且HeadView没有正在刷新，才可以点击加载更多。
                        if (mFooterState != FOOTER_LOADING && mHeadState != HEADER_REFRESHING) {
                            mFooterState = FOOTER_LOADING;
                            onLoadMore();
                        }
                    } else if (mFooterState != FOOTER_LOADING) {
                        // 当不能下拉刷新时，FootView不正在加载时，才可以点击加载更多。
                        mFooterState = FOOTER_LOADING;
                        onLoadMore();
                    }
                }
            }
        });

        addFooterView(mFooterView, null, false);

        if (mIsAutoLoadMore) {
            mFooterState = FOOTER_AUTO_LOAD_DONE;
        } else {
            mFooterState = FOOTER_MANUAL_LOAD_DONE;
        }
    }

    /**
     * 测量HeadView宽高(注意：此方法仅适用于LinearLayout)
     *
     * @param view
     */
    private void measureView(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int childWidthSpec = ViewGroup.getChildMeasureSpec(0, 0, params.width);

        int pHeight = params.height;
        int childHeightSpec;
        if (pHeight > 0) {
            childHeightSpec = MeasureSpec.makeMeasureSpec(pHeight,MeasureSpec.EXACTLY);
        } else {
            childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        }
        view.measure(childWidthSpec, childHeightSpec);
    }

    /**
     * 监听listview的滑动状态
     * <br>1.为了判断滑动到ListView底部没
     * <br>2.根据下滑的距离设置表针的动画
     */
    @Override
    public void onScroll(AbsListView pView, int pFirstVisibleItem,int pVisibleItemCount, int pTotalItemCount) {
        mFirstItemIndex = pFirstVisibleItem;
        mLastItemIndex = pFirstVisibleItem + pVisibleItemCount - 2;
        mCount = pTotalItemCount - 2;
        if (pTotalItemCount > pVisibleItemCount) {
            mEnoughCount = true;
        } else {
            mEnoughCount = false;
        }
        cProgress.setClockByPaddingTop(-1*mHeadViewHeight,mHeaderView.getPaddingTop());
    }

    @Override
    public void onScrollStateChanged(AbsListView pView, int pScrollState) {
        if (mScrollToLoadMore == false) {
            return;
        }
        if (mCanLoadMore) {
            // 存在加载更多功能
            if (mLastItemIndex == mCount && pScrollState == SCROLL_STATE_IDLE) {
                // SCROLL_STATE_IDLE = 0 表示 滑动停止
                if (mFooterState != FOOTER_LOADING) {
                    if (mIsAutoLoadMore) {
                        //可以自动加载更多的情况
                        if (mCanRefresh) {
                            // 可以下拉刷新的情况下再判断是否正在下拉刷新
                            if (mHeadState != HEADER_REFRESHING) {
                                mFooterState = FOOTER_LOADING;
                                onLoadMore();
                                changeFooterViewByState();
                            }
                        } else {
                            // 不能下拉刷新的情况下，我们直接进行加载更多。
                            mFooterState = FOOTER_LOADING;
                            onLoadMore();
                            changeFooterViewByState();
                        }
                    } else {
                        // 不能自动加载更多情况，我们让FooterView显示 “点击加载”
                        // FootView显示 : 点击加载 ---> 加载中...
                        mFooterState = FOOTER_MANUAL_LOAD_DONE;
                        changeFooterViewByState();
                    }
                }
            }
        } else if (mFooterView != null && mFooterView.getVisibility() == VISIBLE) {
            // 突然关闭加载更多功能之后，我们要移除FootView。
            mFooterView.setVisibility(View.GONE);
            this.removeFooterView(mFooterView);
        }
    }

    /**
     * 主要更新一下刷新时间啦！
     *
     * @param adapter
     */
    @Override
    public void setAdapter(ListAdapter adapter) {
        // listview重设数据时更新下刷新时间
        mLastUpdatedTextView.setText("上次更新:"+ new SimpleDateFormat(DATE_FORMAT_STR, Locale.CHINA).format(new Date()));
        super.setAdapter(adapter);
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (mCanRefresh) {
            if (mCanLoadMore && mFooterState == FOOTER_LOADING) {
                // 如果存在加载更多功能，并且当前正在加载更多，默认不允许下拉刷新，必须加载完毕后才能使用。
                return super.onTouchEvent(event);
            }
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (mFirstItemIndex == 0 && !mIsRecored) {
                        mIsRecored = true;
                        mStartY = (int) event.getY();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (mHeadState != HEADER_REFRESHING) {
                        if (mHeadState == HEADER_REFRESHING_DONE) {

                        }
                        if (mHeadState == PULL_TO_REFRESH) {
                            mHeadState = HEADER_REFRESHING_DONE;
                            changeHeaderViewByState();
                        }
                        if (mHeadState == RELEASE_TO_REFRESH) {
                            mHeadState = HEADER_REFRESHING;
                            changeHeaderViewByState();
                            onRefresh();
                        }
                    }

                    mIsRecored = false;
                    mIsBack = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int tempY = (int) event.getY();

                    if (!mIsRecored && mFirstItemIndex == 0) {
                        mIsRecored = true;
                        mStartY = tempY;
                    }

                    if (mHeadState != HEADER_REFRESHING && mIsRecored) {
                        // 保证在设置padding的过程中，当前的位置一直是在head，否则如果当列表超出屏幕的话，当在上推的时候，列表会同时进行滚动
                        if (mHeadState == RELEASE_TO_REFRESH) {
                            // 可以松手去刷新了
                            setSelection(0);

                            if (((tempY - mStartY) / RATIO < mHeadViewHeight) && (tempY - mStartY) > 0) {
                                // 往上推了，推到了屏幕足够掩盖head的程度，但是还没有推到全部掩盖的地步
                                mHeadState = PULL_TO_REFRESH;
                                changeHeaderViewByState();
                            } else if (tempY - mStartY <= 0) {
                                // 一下子推到顶了
                                mHeadState = HEADER_REFRESHING_DONE;
                                changeHeaderViewByState();
                            }
                            // 往下拉了，或者还没有上推到屏幕顶部掩盖head的地步
                        }
                        if (mHeadState == PULL_TO_REFRESH) {
                            // 还没有到达显示松开刷新的时候
                            setSelection(0);

                            if ((tempY - mStartY) / RATIO >= mHeadViewHeight) {
                                // 下拉到可以进入RELEASE_TO_REFRESH的状态
                                mHeadState = RELEASE_TO_REFRESH;
                                mIsBack = true;
                                changeHeaderViewByState();
                            } else if (tempY - mStartY <= 0) {
                                mHeadState = HEADER_REFRESHING_DONE;
                                changeHeaderViewByState();
                            }
                        }
                        if (mHeadState == HEADER_REFRESHING_DONE) {
                            if (tempY - mStartY > 0) {
                                mHeadState = PULL_TO_REFRESH;
                                changeHeaderViewByState();
                            }
                        }

                        //改变headerview的padding显示多少内容
                        if (mHeadState == PULL_TO_REFRESH) {
                            mHeaderView.setPadding(0, -1 * mHeadViewHeight+ (tempY - mStartY) / RATIO, 0, 0);
                        }
                        if (mHeadState == RELEASE_TO_REFRESH) {
                            mHeaderView.setPadding(0, (tempY - mStartY) / RATIO- mHeadViewHeight, 0, 0);
                        }
                    }
                    break;
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     * 根据各种状态改变headerview显示内容
     */
    private void changeHeaderViewByState() {
        switch (mHeadState) {
            case RELEASE_TO_REFRESH:
                // 释放刷新状态
                mTipsTextView.setVisibility(View.VISIBLE);
                mLastUpdatedTextView.setVisibility(View.VISIBLE);
                if (RELEASE_TO_REFRESH_TXT.equals("")) {
                    mTipsTextView.setText(R.string.pull_to_refresh_release_label);
                } else {
                    mTipsTextView.setText(RELEASE_TO_REFRESH_TXT);
                }
                break;
            case PULL_TO_REFRESH:
                //下拉刷新状态
                mTipsTextView.setVisibility(View.VISIBLE);
                mLastUpdatedTextView.setVisibility(View.VISIBLE);
                if (mIsBack) {
                    // 是由RELEASE_TO_REFRESH状态转变来的
                    mIsBack = false;
                }
                if (PULL_TO_REFRESH_TXT.equals("")) {
                    mTipsTextView.setText(R.string.pull_to_refresh_pull_label);
                } else {
                    mTipsTextView.setText(PULL_TO_REFRESH_TXT);
                }
                break;
            case HEADER_REFRESHING:
                //正在刷新中
                mHeaderView.setPadding(0, 0, 0, 0);
                mTipsTextView.setText(R.string.pull_to_refresh_refreshing_label);
                mLastUpdatedTextView.setVisibility(View.VISIBLE);
                break;
            case HEADER_REFRESHING_DONE:
                //下拉刷新完成
                mHeaderView.setPadding(0, -1 * mHeadViewHeight, 0, 0);
                cProgress.setClockToZero();
                if (PULL_TO_REFRESH_TXT.equals("")) {
                    mTipsTextView.setText(R.string.pull_to_refresh_pull_label);
                } else {
                    mTipsTextView.setText(PULL_TO_REFRESH_TXT);
                }
                mLastUpdatedTextView.setVisibility(View.VISIBLE);
                break;
        }
    }

    /**
     * 设置加载完成，底部显示"没有更多内容"
     */
    public void setFooterViewNoMore() {
        if (null != mFooterLoadTipsTextView) {
            mFooterLoadTipsTextView.setText(R.string.p2refresh_end_load_more);
        }
        if (null != mFooterView) {
            mFooterView.setClickable(false);
        }
        mScrollToLoadMore = false;
    }

    /**
     * 重置、初始化底部显示内容，底部显示"点击加载"
     */
    public void resetFooterView() {
        if (null != mFooterLoadTipsTextView) {
            mFooterLoadTipsTextView.setText(R.string.p2refresh_end_click_load_more);
        }
        if (null != mFooterView) {
            mFooterView.setClickable(true);
        }
        mScrollToLoadMore = true;
    }

    /**
     * 设置底部内容，底部显示"加载失败，点击重试"
     */
    public void setFooterViewError() {
        mFooterState = FOOTER_LOAD_ERROR;
        changeFooterViewByState();
    }

    /**
     * 清除底部设置的内容、属性
     */
    public void clearFooterView() {
        if (mCanLoadMore && getFooterViewsCount() > 0) {
            mFooterLoadTipsTextView.setText("");
            mFooterView.setOnClickListener(null);
            mScrollToLoadMore = false;
        }
    }

    /**
     * 根据各种状态改变footerview显示内容
     */
    private void changeFooterViewByState() {
        if (mCanLoadMore) {
            // 允许加载更多
            switch (mFooterState) {
                case FOOTER_LOADING:
                    //加载更多中
                    mFooterLoadTipsTextView.setText(R.string.p2refresh_doing_end_refresh);
                    mFooterLoadTipsTextView.setVisibility(View.VISIBLE);
                    mFooterLoadProgressBar.setVisibility(View.VISIBLE);
                    break;
                case FOOTER_MANUAL_LOAD_DONE:
                    //手动点击加载更多之后
                    mFooterLoadTipsTextView.setText(R.string.p2refresh_end_load_more);
                    mFooterLoadTipsTextView.setVisibility(View.VISIBLE);
                    mFooterLoadProgressBar.setVisibility(View.GONE);
                    mFooterView.setVisibility(View.VISIBLE);
                    break;
                case FOOTER_AUTO_LOAD_DONE:
                    // 自动刷新完成
                    mFooterLoadTipsTextView.setText(R.string.p2refresh_head_load_more);
                    mFooterLoadTipsTextView.setVisibility(View.VISIBLE);
                    mFooterLoadProgressBar.setVisibility(View.GONE);
                    mFooterView.setVisibility(View.VISIBLE);

                    // 当所有item的高度小于ListView本身的高度时，要隐藏掉FootView
                    // if (enoughCount) {
                    // mEndRootView.setVisibility(View.VISIBLE);
                    // } else {
                    // mEndRootView.setVisibility(View.GONE);
                    // }

                    break;
                case FOOTER_LOAD_ERROR:
                    // 刷新失败
                    mFooterLoadTipsTextView.setText(R.string.p2refresh_end_load_more_error);
                    mFooterLoadTipsTextView.setVisibility(View.VISIBLE);
                    mFooterLoadProgressBar.setVisibility(View.GONE);
                    mFooterView.setVisibility(View.VISIBLE);
                    break;
            }
        }
    }

    /**
     * 手动设置下拉刷新的显示文字
     * @param txt
     */
    public void setPullToRefreshTxt(String txt) {
        PULL_TO_REFRESH_TXT = txt;
    }

    /**
     * 手动显示释放刷新的显示文字
     * @param txt
     */
    public void setReleaseToRefreshTxt(String txt) {
        RELEASE_TO_REFRESH_TXT = txt;
    }

    /**
     * 设置正在刷新中
     */
    public void setPullDownRefreshing() {
        mHeadState = HEADER_REFRESHING;
        changeHeaderViewByState();
        onRefresh();
    }

    /**
     * 下拉刷新接口
     */
    public interface OnRefreshListener {
        public void onRefresh();
    }

    /**
     * 上拉加载更多接口
     */
    public interface OnLoadMoreListener {
        public void onLoadMore();
    }

    public void setOnRefreshListener(OnRefreshListener pRefreshListener) {
        if (pRefreshListener != null) {
            mRefreshListener = pRefreshListener;
            mCanRefresh = true;
        }
    }

    public void setOnLoadmoreListener(OnLoadMoreListener pLoadMoreListener) {
        if (pLoadMoreListener != null) {
            mLoadMoreListener = pLoadMoreListener;
        }
    }

    /**
     * 正在下拉刷新
     */
    private void onRefresh() {
        if (mRefreshListener != null) {
            mRefreshListener.onRefresh();
            cProgress.setStartAutoRotate();
            isPullDownRefreshing = true;
        }
    }

    /**
     * 正在加载更多
     */
    private void onLoadMore() {
        if (mLoadMoreListener != null) {
            isPullUpLoading = true;
            mFooterLoadTipsTextView.setText(R.string.p2refresh_doing_end_refresh);
            mFooterLoadTipsTextView.setVisibility(View.VISIBLE);
            mFooterLoadProgressBar.setVisibility(View.VISIBLE);
            mLoadMoreListener.onLoadMore();
        }
    }

    /**
     * 下拉刷新完成
     */
    public void onRefreshComplete() {
        // 下拉刷新后是否显示第一条Item
        if (mIsMoveToFirstItemAfterRefresh) {
            setSelection(0);
        }
        isPullDownRefreshing = false;
        mHeadState = HEADER_REFRESHING_DONE;
        // 设置最近更新时间
        mLastUpdatedTextView.setText("上次更新:"+ new SimpleDateFormat(DATE_FORMAT_STR, Locale.CHINA).format(new Date()));
        changeHeaderViewByState();
    }

    /**
     * 加载更多完成
     */
    public void onLoadMoreComplete() {
        if (mIsAutoLoadMore) {
            mFooterState = FOOTER_AUTO_LOAD_DONE;
        } else {
            mFooterState = FOOTER_MANUAL_LOAD_DONE;
        }
        isPullUpLoading = false;
        changeFooterViewByState();
    }

    /**
     * 获取是否正在下拉刷新中
     * @return
     */
    public boolean isPullDownRefreshing() {
        return isPullDownRefreshing;
    }

    /**
     * 设置是否正在上拉加载更多中
     * @return
     */
    public boolean isPullUpLoading() {
        return isPullUpLoading;
    }

    /**
     * 是否可以加载更多
     * @return
     */
    public boolean isCanLoadMore() {
        return mCanLoadMore;
    }

    /**
     * 设置是否可以加载更多
     * @param pCanLoadMore
     */
    public void setCanLoadMore(boolean pCanLoadMore) {
        mCanLoadMore = pCanLoadMore;
        if (mCanLoadMore && getFooterViewsCount() == 0) {
            addFooterView();
        }
    }

    public boolean isEnoughCount() {
        return enoughCount;
    }

    public void setEnoughCount(boolean enoughCount) {
        this.enoughCount = enoughCount;
    }

    /**
     * 是否可以下拉刷新
     * @return
     */
    public boolean isCanRefresh() {
        return mCanRefresh;
    }

    /**
     * 获取是否可以下拉刷新
     * @param pCanRefresh
     */
    public void setCanRefresh(boolean pCanRefresh) {
        mCanRefresh = pCanRefresh;
    }

    /**
     * 是否可以自动加载更多
     * @return
     */
    public boolean isAutoLoadMore() {
        return mIsAutoLoadMore;
    }

    /**
     * 获取是否可以自动加载更多
     * @param pIsAutoLoadMore
     */
    public void setAutoLoadMore(boolean pIsAutoLoadMore) {
        mIsAutoLoadMore = pIsAutoLoadMore;
    }

    /**
     * 刷新完成后是否到第一条
     * @return
     */
    public boolean isMoveToFirstItemAfterRefresh() {
        return mIsMoveToFirstItemAfterRefresh;
    }

    /**
     * 设置刷新完成后是否到第一条
     * @return
     */
    public void setMoveToFirstItemAfterRefresh(
        boolean pIsMoveToFirstItemAfterRefresh) {
        mIsMoveToFirstItemAfterRefresh = pIsMoveToFirstItemAfterRefresh;
    }

    /**
     * 是否到加载更多位置
     * @return
     */
    public boolean isScrollToLoadMore() {
        return mScrollToLoadMore;
    }

    /**
     * 获取是否到加载更多位置
     * @return
     */
    public void setScrollToLoadMore(boolean mScrollToLoadMore) {
        this.mScrollToLoadMore = mScrollToLoadMore;
    }

}
