package com.example.zjh.qhb;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TreeMap;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import android.widget.Toast;


@SuppressLint("NewApi")
public class QiangHongBaoService extends AccessibilityService
{

	private static final String WECHAT_OPEN_EN = "Open";
	private static final String WECHAT_OPENED_EN = "You've opened";
	private final static String QQ_DEFAULT_CLICK_OPEN = "点击拆开";
	private final static String QQ_HONG_BAO_PASSWORD = "口令红包";
	private final static String QQ_CLICK_TO_PASTE_PASSWORD = "点击输入口令";
	private final  static String QQ_HONGBAO_TEXT_KEY = "[QQ红包]";
	private boolean mLuckyMoneyReceived;
	private String lastFetchedHongbaoId = null;
	private long lastFetchedTime = 0;
	private static final int MAX_CACHE_TOLERANCE = 5000;
	private AccessibilityNodeInfo rootNodeInfo;
	private List<AccessibilityNodeInfo> mReceiveNode;

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public void recycle(AccessibilityNodeInfo info) {
		if (info.getChildCount() == 0) {
      /*这个if代码的作用是：匹配“点击输入口令的节点，并点击这个节点”*/
			if(info.getText()!=null&&info.getText().toString().equals(QQ_CLICK_TO_PASTE_PASSWORD)) {
				info.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
			}
      /*这个if代码的作用是：匹配文本编辑框后面的发送按钮，并点击发送口令*/
			if (info.getClassName().toString().equals("android.widget.Button") && info.getText().toString().equals("发送")) {
				info.performAction(AccessibilityNodeInfo.ACTION_CLICK);
			}
		} else {
			for (int i = 0; i < info.getChildCount(); i++) {
				if (info.getChild(i) != null) {
					recycle(info.getChild(i));
				}
			}
		}
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {

		final int eventType = event.getEventType(); // ClassName:
												// com.tencent.mm.ui.LauncherUI

		Log.w("事件类型","********************************************");
		Log.w("事件类型",event.getClassName().toString());
		Log.w("事件类型","********************************************");

		// 通知栏事件
		if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
			List<CharSequence> texts = event.getText();
			if (!texts.isEmpty()) {
				for (CharSequence t : texts) {
					String text = String.valueOf(t);
					if (text.contains(QQ_HONGBAO_TEXT_KEY)) {
						openNotify(event);
						break;
					}
				}
			}
			return;
		}



/*
		if(event.getClassName().toString().equals("android.view.View")){
			List<CharSequence> texts = event.getText();
			if (!texts.isEmpty()) {
				for (CharSequence t : texts) {
					String text = String.valueOf(t);
					if (text.contains(QQ_HONGBAO_TEXT_KEY)) {
						Log.w("提醒","打开列表界面红包");
						event.getSource().performAction(AccessibilityNodeInfo.ACTION_CLICK);
						break;
					}
				}
			}
			return;
		}

*/




		this.rootNodeInfo = event.getSource();
		if (rootNodeInfo == null) {
			return;
		}
		mReceiveNode = null;
		checkNodeInfo();
    /* 如果已经接收到红包并且还没有戳开 */
		if (mLuckyMoneyReceived && (mReceiveNode != null)) {
			int size = mReceiveNode.size();
			if (size > 0) {
				String id = getHongbaoText(mReceiveNode.get(size - 1));
				long now = System.currentTimeMillis();
				if (this.shouldReturn(id, now - lastFetchedTime))
					return;
				lastFetchedHongbaoId = id;
				lastFetchedTime = now;
				AccessibilityNodeInfo cellNode = mReceiveNode.get(size - 1);
				if (cellNode.getText().toString().equals("口令红包已拆开")) {
					return;
				}
				cellNode.getParent().performAction(AccessibilityNodeInfo.ACTION_CLICK);
				if (cellNode.getText().toString().equals(QQ_HONG_BAO_PASSWORD)) {
					AccessibilityNodeInfo rowNode = getRootInActiveWindow();
					if (rowNode == null) {
						Log.e("节点遍历", "noteInfo is　null");
						return;
					} else {
						recycle(rowNode);
					}
				}
				mLuckyMoneyReceived = false;
			}
		}




		//在和其他好友聊天界面
		if(event.getClassName().toString().equals("android.widget.TextView")){
			AccessibilityNodeInfo node=event.getSource();
			if(node.getText()==null){
				return;
			}
			Log.w("消息",node.getText().toString());
			if(node.getText().toString().contains(QQ_HONGBAO_TEXT_KEY)){
				Log.w("提醒","打开好友红包");
				event.getSource().performAction(AccessibilityNodeInfo.ACTION_CLICK);
			}
			return;
		}
	}

	private void checkNodeInfo() {
		if (rootNodeInfo == null) {
			return;
		}
    /* 聊天会话窗口，遍历节点匹配“点击拆开”，“口令红包”，“点击输入口令” */
		List<AccessibilityNodeInfo> nodes1 = this.findAccessibilityNodeInfosByTexts(this.rootNodeInfo, new String[]{QQ_DEFAULT_CLICK_OPEN, QQ_HONG_BAO_PASSWORD, QQ_CLICK_TO_PASTE_PASSWORD, "发送"});
		if (!nodes1.isEmpty()) {
			String nodeId = Integer.toHexString(System.identityHashCode(this.rootNodeInfo));
			if (!nodeId.equals(lastFetchedHongbaoId)) {
				mLuckyMoneyReceived = true;
				mReceiveNode = nodes1;
			}        return;
		}
	}

	private String getHongbaoText(AccessibilityNodeInfo node) {
      /* 获取红包上的文本 */
		String content;
		try {
			AccessibilityNodeInfo i = node.getParent().getChild(0);
			content = i.getText().toString();
		} catch (NullPointerException npe) {
			return null;
		}
		return content;
	}

	private boolean shouldReturn(String id, long duration) {
		// ID为空
		if (id == null) return true;
		// 名称和缓存不一致
		if (duration < MAX_CACHE_TOLERANCE && id.equals(lastFetchedHongbaoId)) {
			return true;
		}
		return false;
	}

	private List<AccessibilityNodeInfo> findAccessibilityNodeInfosByTexts(AccessibilityNodeInfo nodeInfo, String[] texts) {
		for (String text : texts) {
			if (text == null) continue;
			List<AccessibilityNodeInfo> nodes = nodeInfo.findAccessibilityNodeInfosByText(text);
			if (!nodes.isEmpty()) {
				if (text.equals(WECHAT_OPEN_EN) && !nodeInfo.findAccessibilityNodeInfosByText(WECHAT_OPENED_EN).isEmpty()) {
					continue;
				}
				return nodes;
			}
		}
		return new ArrayList<>();
	}

	@Override
	public void onInterrupt() {}



	/** 打开通知栏消息 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void openNotify(AccessibilityEvent event)
	{
		if (event.getParcelableData() == null || !(event.getParcelableData() instanceof Notification))
		{
			return;
		}
		// 将通知栏消息打开
		Notification notification = (Notification) event.getParcelableData();
		PendingIntent pendingIntent = notification.contentIntent;
		try
		{
			pendingIntent.send();
		} catch (PendingIntent.CanceledException e)
		{
			e.printStackTrace();
		}
	}
}
