/*
 * The MIT License (MIT)
 * Copyright (c) 2014 longkai
 * The software shall be used for good, not evil.
 */
package org.catnut.plugin.zhihu;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Html;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.method.LinkMovementMethod;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import org.catnut.R;
import org.catnut.core.CatnutProvider;
import org.catnut.support.HtmlImageGetter;
import org.catnut.support.QuickReturnScrollView;
import org.catnut.util.CatnutUtils;
import org.catnut.util.Constants;

import java.io.File;

/**
 * 知乎条目
 *
 * @author longkai
 */
public class ZhihuItemFragment extends Fragment implements QuickReturnScrollView.Callbacks {
	public static final String TAG = ZhihuItemFragment.class.getSimpleName();

	private static final String[] PROJECTION = new String[]{
			Zhihu.QUESTION_ID,
			Zhihu.ANSWER,
			Zhihu.DESCRIPTION,
			Zhihu.TITLE,
			Zhihu.LAST_ALTER_DATE,
			Zhihu.NICK,
	};

	private static final int ACTION_VIEW_ON_WEB = 1;
	private static final int ACTION_VIEW_ALL_ON_WEB = 2;


	private ScrollSettleHandler mScrollSettleHandler = new ScrollSettleHandler();

	private View mPlaceholderView;
	private View mQuickReturnView;
	private QuickReturnScrollView mQuickReturnLayout;

	private int mMinRawY = 0;
	private int mState = STATE_ON_SCREEN;
	private int mQuickReturnHeight;
	private int mMaxScrollY;

	private long mAnsertId;
	private long mQuestionId;

	public static ZhihuItemFragment getFragment(long id) {
		Bundle args = new Bundle();
		args.putLong(Constants.ID, id);
		ZhihuItemFragment fragment = new ZhihuItemFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mAnsertId = getArguments().getLong(Constants.ID);
		setHasOptionsMenu(true);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		mQuickReturnLayout = (QuickReturnScrollView) inflater.inflate(R.layout.zhihu_item, container, false);
		mPlaceholderView = mQuickReturnLayout.findViewById(R.id.place_holder);
		mQuickReturnView = mQuickReturnLayout.findViewById(android.R.id.title);
		mQuickReturnLayout.setCallbacks(this);
		mQuickReturnLayout.getViewTreeObserver().addOnGlobalLayoutListener(
				new ViewTreeObserver.OnGlobalLayoutListener() {
					@Override
					public void onGlobalLayout() {
						onScrollChanged(mQuickReturnLayout.getScrollY());
						mMaxScrollY = mQuickReturnLayout.computeVerticalScrollRange()
								- mQuickReturnLayout.getHeight();
						mQuickReturnHeight = mQuickReturnView.getHeight();
					}
				}
		);
		return mQuickReturnLayout;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		final TextView title = (TextView) view.findViewById(android.R.id.title);
		final TextView question = (TextView) view.findViewById(R.id.question);
		final TextView author = (TextView) view.findViewById(R.id.author);
		final TextView content = (TextView) view.findViewById(android.R.id.content);
		final TextView lastAlterDate = (TextView) view.findViewById(R.id.last_alter_date);

		registerForContextMenu(title);
		title.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				getActivity().openContextMenu(title);
			}
		});

		(new Thread(new Runnable() {
			@Override
			public void run() {
				Cursor cursor = getActivity().getContentResolver().query(
						CatnutProvider.parse(Zhihu.MULTIPLE),
						PROJECTION,
						Zhihu.ANSWER_ID + "=" + mAnsertId,
						null,
						null
				);
				if (cursor.moveToNext()) {
					mQuestionId = cursor.getLong(cursor.getColumnIndex(Zhihu.QUESTION_ID));
					final String _title = cursor.getString(cursor.getColumnIndex(Zhihu.TITLE));
					final String _question = cursor.getString(cursor.getColumnIndex(Zhihu.DESCRIPTION));
					final String _nick = cursor.getString(cursor.getColumnIndex(Zhihu.NICK));
					final String _content = cursor.getString(cursor.getColumnIndex(Zhihu.ANSWER));
					final long _lastAlterDate = cursor.getLong(cursor.getColumnIndex(Zhihu.LAST_ALTER_DATE));
					mScrollSettleHandler.post(new Runnable() {
						@Override
						public void run() {
							title.setText(_title);
							if (_title.length() > 30) {
								title.setTextSize(18);
							}

							String location = null;
							try {
								location = CatnutUtils.mkdir(getActivity(), Zhihu.CACHE_IMAGE_LOCATION);
							} catch (Exception e) {
							}

							if (TextUtils.isEmpty(_question)) {
								question.setVisibility(View.GONE);
							} else {
								question.setText(Html.fromHtml(_question, new HtmlImageGetter(question, location), null));
							}
							CatnutUtils.removeLinkUnderline(question);
							question.setMovementMethod(LinkMovementMethod.getInstance());
							author.setText(_nick);
							content.setText(Html.fromHtml(_content, new HtmlImageGetter(content, location), null));
							content.setMovementMethod(LinkMovementMethod.getInstance());
							CatnutUtils.removeLinkUnderline(content);
							lastAlterDate.setText(DateUtils.getRelativeTimeSpanString(_lastAlterDate));
						}
					});
				}
				cursor.close();
			}
		})).start();
	}

	@Override
	public void onStart() {
		super.onStart();
		getActivity().getActionBar().setTitle(getString(R.string.read_zhihu));
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		menu.add(Menu.NONE, ACTION_VIEW_ALL_ON_WEB, Menu.NONE, getString(R.string.view_all_answer));
		menu.add(Menu.NONE, ACTION_VIEW_ON_WEB, Menu.NONE, getString(R.string.zhihu_view_on_web));
		super.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		viewOutside(item.getItemId());
		return super.onContextItemSelected(item);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		menu.add(Menu.NONE, ACTION_VIEW_ALL_ON_WEB, Menu.NONE, getString(R.string.view_all_answer))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
		menu.add(Menu.NONE, ACTION_VIEW_ON_WEB, Menu.NONE, getString(R.string.zhihu_view_on_web))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		viewOutside(item.getItemId());
		return super.onOptionsItemSelected(item);
	}

	private void viewOutside(int which) {
		switch (which) {
			case ACTION_VIEW_ON_WEB:
				startActivity(new Intent(Intent.ACTION_VIEW,
						Uri.parse("http://www.zhihu.com/question/" + mQuestionId + "/answer/" + mAnsertId)));
				break;
			case ACTION_VIEW_ALL_ON_WEB:
				startActivity(new Intent(Intent.ACTION_VIEW,
						Uri.parse("http://www.zhihu.com/question/" + mQuestionId)));
				break;
			default:
				break;
		}
	}

	@Override
	public void onScrollChanged(int scrollY) {
		scrollY = Math.min(mMaxScrollY, scrollY);

		mScrollSettleHandler.onScroll(scrollY);

		int rawY = mPlaceholderView.getTop() - scrollY;
		int translationY = 0;

		switch (mState) {
			case STATE_OFF_SCREEN:
				if (rawY <= mMinRawY) {
					mMinRawY = rawY;
				} else {
					mState = STATE_RETURNING;
				}
				translationY = rawY;
				break;

			case STATE_ON_SCREEN:
				if (rawY < -mQuickReturnHeight) {
					mState = STATE_OFF_SCREEN;
					mMinRawY = rawY;
				}
				translationY = rawY;
				break;

			case STATE_RETURNING:
				translationY = (rawY - mMinRawY) - mQuickReturnHeight;
				if (translationY > 0) {
					translationY = 0;
					mMinRawY = rawY - mQuickReturnHeight;
				}

				if (rawY > 0) {
					mState = STATE_ON_SCREEN;
					translationY = rawY;
				}

				if (translationY < -mQuickReturnHeight) {
					mState = STATE_OFF_SCREEN;
					mMinRawY = rawY;
				}
				break;
		}
		mQuickReturnView.animate().cancel();
		mQuickReturnView.setTranslationY(translationY + scrollY);
	}

	@Override
	public void onDownMotionEvent() {
		mScrollSettleHandler.setSettleEnabled(false);
	}

	@Override
	public void onUpOrCancelMotionEvent() {
		mScrollSettleHandler.setSettleEnabled(true);
		mScrollSettleHandler.onScroll(mQuickReturnLayout.getScrollY());
	}

	// quick return animation
	private class ScrollSettleHandler extends Handler {
		private static final int SETTLE_DELAY_MILLIS = 100;

		private int mSettledScrollY = Integer.MIN_VALUE;
		private boolean mSettleEnabled;

		public void onScroll(int scrollY) {
			if (mSettledScrollY != scrollY) {
				// Clear any pending messages and post delayed
				removeMessages(0);
				sendEmptyMessageDelayed(0, SETTLE_DELAY_MILLIS);
				mSettledScrollY = scrollY;
			}
		}

		public void setSettleEnabled(boolean settleEnabled) {
			mSettleEnabled = settleEnabled;
		}

		@Override
		public void handleMessage(Message msg) {
			// Handle the scroll settling.
			if (STATE_RETURNING == mState && mSettleEnabled) {
				int mDestTranslationY;
				if (mSettledScrollY - mQuickReturnView.getTranslationY() > mQuickReturnHeight / 2) {
					mState = STATE_OFF_SCREEN;
					mDestTranslationY = Math.max(
							mSettledScrollY - mQuickReturnHeight,
							mPlaceholderView.getTop());
				} else {
					mDestTranslationY = mSettledScrollY;
				}

				mMinRawY = mPlaceholderView.getTop() - mQuickReturnHeight - mDestTranslationY;
				mQuickReturnView.animate().translationY(mDestTranslationY);
			}
			mSettledScrollY = Integer.MIN_VALUE; // reset
		}
	}
}