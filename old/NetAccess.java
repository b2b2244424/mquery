package com.minephone.network;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.ActivityManager;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.net.http.AndroidHttpClient;
import android.os.Environment;
import android.text.TextUtils;
import android.widget.ImageView;

import com.android.volley.Cache.Entry;
import com.android.volley.Network;
import com.android.volley.Request.Method;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.HttpClientStack;
import com.android.volley.toolbox.HttpStack;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.Volley;
import com.minephone.volley.R;

/**
 * 网络请求类
 * 
 * @author ping
 * @create 2014-6-23 上午9:36:02
 */
public class NetAccess {
	private static final String TAG = "NetAccess";

	private static final int LOADINGIMAGE = R.drawable.bg_loading_image;

	private static final int ERRORIMAGE = R.drawable.bg_error_image;

	private static final int IMAGEMAXMEASURE = 750;// 图片最大尺寸,0则不限制
	
	private static final String CachePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/mquery";// volley缓存目录

	private static RequestQueue mRequestQueue;// 主要的请求类

	private static ImageLoader mImageLoader;// 图片加载器

	private static BitmapLruCache mLruCache;// 图片缓存器

	private static ImageRequest mImageRequest;// 图片请求(一直修改)

	private Context mcontext;

	private Map<String, String> heads;// 提交头

	private Map<String, String> params;// 提交的参数

	private String flag;// 请求标示
	
	private String cachekey;//缓存的key

	private String cookies;// 提交的cookies

	private int timeout = 9000;// volley超时时间默认25秒

	private Dialog mdialog;// 加载窗口

	private NetAccessListener listener;// 回调方法

	private JsonRequest request;// vollery自定义请求类

	public NetAccess(Context context) {
		mcontext = context;
	}

	/**
	 * 获取vollery自定义请求的类(可以获取请求各种信息)
	 * 
	 * @author ping
	 * @create 2014-4-22 下午5:18:43
	 * @param request
	 */
	public JsonRequest getRequest() {
		return request;
	}

	/**
	 * 自定义缓存key
	 * @author ping
	 * @create 2014-11-13 上午10:43:37
	 * @param key
	 * @return
	 */
	public NetAccess setCachekey(String key) {
		cachekey = key;
		return this;
	}

	
	/**
	 * 获取返回的数据的请求头 NetAccess.getReHeaders();
	 * 
	 * @author ping
	 * @create 2014-4-22 下午5:20:51
	 * @return
	 */
	public Map<String, String> getReHeaders() {
		Map<String, String> data = null;
		if (request != null) {
			data = request.getRe_header();
		}
		return data;
	}

	/**
	 * 获取服务器给客户端的cookie
	 * @author ping
	 * @create 2014-10-14 下午3:31:30
	 * @return
	 */
	public String getRecookies() {
		if (request != null) {
			return request.getRe_cookies();
		}
		return null;
	}
	
	/**
	 * 取消一个请求
	 * @author ping
	 * @create 2014-11-12 下午10:15:10
	 */
	public void cancel () {
		if (request != null) {
			request.cancel();
		}
	}
	
	/**
	 * 取消tag标记的请求
	 * @author ping
	 * @create 2014-11-12 下午10:15:31
	 * @param flag
	 */
	public static void cancel(String flag) {
		if (mRequestQueue != null) {
			mRequestQueue.cancelAll(flag);
		}
	}
	
	/**
	 * 回调接口
	 * 
	 * @author ping 2014-4-9 下午10:32:42
	 */
	public interface NetAccessListener {
		public void onAccessComplete(boolean success, String object,
				VolleyError error, String flag);
	}

	/**
	 * @param url
	 *            根据Url删除缓存
	 */
	public static void clearCache(String url) {
		try {
			if (url == null) {
				Set<String> mset = mLruCache.snapshot().keySet();
				String[] keys = mset.toArray(new String[mset.size()]);
				for (String key : keys) {
					mLruCache.remove(key);
				}

				mRequestQueue.getCache().clear();
			} else {
				mLruCache.remove(url);
				mRequestQueue.getCache().remove(url);
			}
		} catch (Exception e) {
			// 可能出现
			// mLruCache、mRequestQueue空指针
		}
	}

	/**
	 * 初始化
	 * 
	 * @author ping 2014-4-9 下午6:41:52
	 * @param context
	 * @return
	 */
	public static NetAccess request(Context context) {
		checkVar(context);
		return new NetAccess(context);
	}

	private static void checkVar(Context context) {
		// 网络请求类初始化
		if (mRequestQueue == null) {
			if (android.os.Environment.getExternalStorageState().equals(
					android.os.Environment.MEDIA_MOUNTED)) {
				File file = new File(CachePath);
				DiskBasedCache cache = new DiskBasedCache(file,
						20 * 1024 * 1024);
				HttpStack stack = null;
				if (android.os.Build.VERSION.SDK_INT >= 9) {
					stack = new HurlStack();
				} else {
					stack = new HttpClientStack(
							AndroidHttpClient.newInstance("volley/0"));
				}
				Network network = new BasicNetwork(stack);
				mRequestQueue = new RequestQueue(cache, network);
				mRequestQueue.start();
			} else {
				// 无sd卡 默认5Mb：data/data/package/cache/volley
				if (context != null) {
					mRequestQueue = Volley.newRequestQueue(context);
				}
			}
		}

		// 图片缓存类初始化
		if (mLruCache == null) {
			int cacheSize = 0;
			if (context == null) {
				// LruCache通过构造函数传入缓存值，以KB为单位。
				int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
				// 使用最大可用内存值的1/8作为缓存的大小。
				cacheSize = maxMemory / 8;
			} else {
				// Use 1/8th of the available memory for this memory cache.
				int memClass = ((ActivityManager) context
						.getSystemService(Context.ACTIVITY_SERVICE))
						.getMemoryClass();
				cacheSize = 1024 * 1024 * memClass / 8;// 0.5>>50331648
			}
			mLruCache = new BitmapLruCache(cacheSize);
		}
		// 图片(缓存)请求类初始化
		if (mImageLoader == null) {
			mImageLoader = new ImageLoader(mRequestQueue, mLruCache);
		}
	}

	/**
	 * 获取缓存
	 * 
	 * @author ping 2014-4-10 下午1:57:11
	 * @param url
	 * @return
	 */
	public static String getCache(String urlorkey) {
		String result = null;
		try {
			// 获取缓存
			Entry cachedata = mRequestQueue.getCache().get(urlorkey);
			if (cachedata != null) {
				if (cachedata.data != null) {
					result = JsonRequest.decode(cachedata.responseHeaders, cachedata.data);
				}
			}
		} catch (Exception e) {
			// 可能出现
			// mRequestQueue空指针
		}
		return result;
	}

	/**
	 * 获取缓存
	 * 
	 * @author ping
	 * @create 2014-5-24 下午6:04:09
	 * @param url
	 * @param cls
	 * @return
	 */
	public static <T> T getCache(String url, Class<T> cls) {
		T result = null;
		try {
			// 获取缓存
			Entry cachedata = mRequestQueue.getCache().get(url);
			if (cachedata != null) {
				if (cachedata.data != null) {
					Object obj = null;
					if (cls.equals(Bitmap.class)) {
						if (IMAGEMAXMEASURE != 0) {
							BitmapFactory.Options opts = new BitmapFactory.Options();
							opts.inJustDecodeBounds = true;
							BitmapFactory.decodeByteArray(cachedata.data, 0,cachedata.data.length, opts);
							opts.inSampleSize = calculateSampleSize(opts,IMAGEMAXMEASURE, IMAGEMAXMEASURE);
							opts.inJustDecodeBounds = false;
							obj = BitmapFactory.decodeByteArray(cachedata.data,0, cachedata.data.length, opts);
						} else {
							obj = BitmapFactory.decodeByteArray(cachedata.data,0, cachedata.data.length);
						}
					} else {
						obj = JsonRequest.decode(cachedata.responseHeaders, cachedata.data);
					}
					result = (T) obj;
				}
			}
		} catch (Exception e) {
			// 可能出现
			// 空指针、 (T) obj;强制失败
		}
		return result;
	}

	/**
	 * 设置请求头
	 * 
	 * @author ping 2014-4-10 上午9:44:42
	 * @param heads
	 * @return
	 */
	public NetAccess setHeaders(Map<String, String> heads) {
		this.heads = heads;
		return this;
	}

	/**
	 * 设置请求内容
	 * 
	 * @author ping 2014-4-10 上午9:44:52
	 * @param params
	 * @return
	 */
	public NetAccess setParams(Map<String, String> params) {
		this.params = params;
		return this;
	}

	/**
	 * 设置请求内容
	 * 
	 * @author ping 2014-4-10 上午9:45:06
	 * @param params
	 * @return
	 */
	public NetAccess setParams2(Map<String, Object> params) {
		Map<String, String> param2 = new HashMap<String, String>();
		if (params != null) {
			for (String key : params.keySet()) {
				param2.put(key, params.get(key) + "");
			}
		}
		this.params = param2;
		return this;
	}

	/**
	 * 设置请求标示
	 * 
	 * @author ping 2014-4-10 上午9:45:16
	 * @param flag
	 * @return
	 */
	public NetAccess setFlag(String flag) {
		this.flag = flag;
		return this;
	}

	/**
	 * 设置请求cookies
	 * 
	 * @author ping 2014-4-10 上午9:46:02
	 * @param cookies
	 * @return
	 */
	public NetAccess setCookies(String cookies) {
		this.cookies = cookies;
		return this;
	}

	public NetAccess setDialog(Dialog dialog) {
		mdialog = dialog;
		return this;
	}
	
	/**
	 * 设置显示加载中Dialog窗口
	 * 
	 * @author ping 2014-4-10 上午9:46:16
	 * @param isShow
	 * @return
	 */
	public NetAccess showDialog(boolean canCancel) {
		return showDialog("加载中", canCancel);
	}
	
	public NetAccess showDialog(int tip, boolean canCancel) {
		showDialog(mcontext.getString(tip),canCancel);
		return this;
	}
	
	public NetAccess showDialog(String tip, boolean canCancel) {
		if (mdialog!=null) {
			if (mdialog.isShowing()) {
				mdialog.dismiss();				
			}
			mdialog.show();
		} else {
			if (tip == null) {
				mdialog = ProgressDialog.show(mcontext, null, "加载中", true,canCancel, null);
			} else {
				mdialog = ProgressDialog.show(mcontext, null, tip, true, canCancel,null);
			}
		}
		return this;
	}
	
	/**
	 * 设置超时时间，默认20秒
	 * 
	 * @author ping 2014-4-10 上午9:47:15
	 * @param timeout
	 * @return
	 */
	public NetAccess setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * 开始get请求
	 * 
	 * @author ping 2014-4-10 上午9:49:01
	 * @param url
	 * @param listener
	 */
	synchronized public void byGet(String url, NetAccessListener listener) {
		url += getParamStr(params);

		MQLog.i(TAG, "gurl-->" + url);
		startrequest(url, Method.GET, false, listener);
	}

	/**
	 * 获取缓存后，在进行get请求
	 * 
	 * @author ping 2014-4-10 上午9:49:10
	 * @param url
	 * @param listener
	 */
	synchronized public void byCacheGet(String url, NetAccessListener listener) {
		url += getParamStr(params);
		String cache = getCache(cachekey == null ? url : cachekey);
		if (cache != null) {
			MQLog.i(TAG, "gcache-->" + cache);
			if (!(mdialog == null || !mdialog.isShowing())) {
				mdialog.dismiss();
			}
			if (listener != null) {
				listener.onAccessComplete(true, cache, null, flag);
			}
		}

		MQLog.i(TAG, "cgurl-->" + url);
		startrequest(url, Method.GET, true, listener);
	}

	/**
	 * 开始post 请求
	 * 
	 * @author ping 2014-4-10 上午9:49:10
	 * @param url
	 * @param listener
	 */
	synchronized public void byPost(String url, NetAccessListener listener) {
		MQLog.i(TAG, "purl-->" + url + getParamStr(params));
		startrequest(url, Method.POST, false, listener);
	}

	/**
	 * 获取缓存后，在进行post请求
	 * 
	 * @author ping 2014-4-10 上午9:49:10
	 * @param url
	 * @param listener
	 */
	synchronized public void byCachePost(String url, NetAccessListener listener) {
		String cache = getCache(cachekey == null ? url : cachekey);
		if (cache != null) {
			MQLog.i(TAG, "pcache-->" + cache);
			if (!(mdialog == null || !mdialog.isShowing())) {
				mdialog.dismiss();
			}
			if (listener != null) {
				listener.onAccessComplete(true, cache, null, flag);
			}
		}

		MQLog.i(TAG, "cpurl-->" + url + getParamStr(params));
		startrequest(url, Method.POST, true, listener);
	}
	
	/**
	 * 直到成功的get请求,如果失败保存到数据库,调用dealfailrequest方法重新请求
	 * 
	 * @author ping
	 * @create 2014-8-29 下午12:24:08
	 * @param url
	 * @param listener
	 */
	synchronized public void byUntilSuccessGet(String url,final NetAccessListener listener) {
		final String murl = url + getParamStr(params);
		MQLog.i(TAG, "usgurl-->" + url);

		NetAccessListener mlistener = new NetAccessListener() {
			@Override
			public void onAccessComplete(boolean success, String object,
					VolleyError error, String flag) {
				if (!success) {
					USRequestUtil.add(mcontext, Method.GET, murl, heads, params);
				}
				if (listener != null) {
					listener.onAccessComplete(success, object, error, flag);
				}
			}
		};
		startrequest(murl, Method.GET, false, mlistener);
	}

	/**
	 * 直到成功的post请求,如果失败保存到数据库,调用dealfailrequest方法重新请求
	 * 
	 * @author ping
	 * @create 2014-8-29 下午12:24:08
	 * @param url
	 * @param listener
	 */
	synchronized public void byUntilSuccessPost(final String url,final NetAccessListener listener) {
		MQLog.i(TAG, "uspurl-->" + url);

		NetAccessListener mlistener = new NetAccessListener() {
			@Override
			public void onAccessComplete(boolean success, String object,
					VolleyError error, String flag) {
				if (!success) {
					USRequestUtil.add(mcontext, Method.POST, url, heads, params);
				}
				if (listener != null) {
					listener.onAccessComplete(success, object, error, flag);
				}
			}
		};
		startrequest(url, Method.POST, false, mlistener);
	}

	/**
	 * 重新请求上次未成功的请求
	 * 
	 * @param listener
	 */
	public void dealFailRequest(NetAccessListener listener) {
		ArrayList<RqData> list = USRequestUtil.listall(mcontext);
		for (RqData data : list) {
			this.params = data.postdata;
			this.heads = data.headdata;
			if (data.method == Method.GET) {
				byUntilSuccessGet(data.url, listener);
			} else {
				byUntilSuccessPost(data.url, listener);
			}
			USRequestUtil.del(mcontext, data.id);
		}
	}

	/**
	 * 开始请求
	 * 
	 * @author ping 2014-4-11 下午12:52:26
	 * @param url
	 *            请求链接(post不包含参数、get包含参数)
	 * @param savecache
	 *            是否缓存
	 */
	private void startrequest(String url, int method,
			boolean savecache, NetAccessListener listener) {
		if (mdialog != null) {
			mdialog.show();
		}

		this.listener = listener;

		ResponseListener relistener = new ResponseListener(this);
		request = new JsonRequest(method, url, relistener, relistener);
		request.setHeaders(heads);
		if (method != Method.GET) {
			request.setParams(params);
		}
		if (cookies==null) {
			cookies = CookieUtil.getCookie(mcontext, GetDomainName(url));
		}
		request.setUrl(url);
		request.setCookies(cookies);
		request.setTimeout(timeout);
		request.setShouldCache(savecache);
		request.setCachekey(cachekey);
		request.setTag(TextUtils.isEmpty(flag) ? TAG : flag);
		mRequestQueue.add(request);
		mRequestQueue.start();
		// request.getReHeaders()
	}

	/**
	 * 请求回调
	 * 
	 * @author ping 2014-4-10 上午9:49:18
	 */
	private static class ResponseListener implements Response.Listener<String>,
			Response.ErrorListener {
		private NetAccess net;

		public ResponseListener(NetAccess netaccess) {
			this.net = netaccess;
		}

		@Override
		public void onResponse(String response) {
			MQLog.i(TAG, "callback-->" + response);

			if (!(net.mdialog == null || !net.mdialog.isShowing())) {
				net.mdialog.dismiss();
			}

			if (net.listener != null) {
				net.listener.onAccessComplete(true, response, null, net.flag);
			}
			
			//数据库返回的参数
			String recookie = net.request.getRe_cookies();
			if (recookie!=null) {
				CookieUtil.setcookie(net.mcontext, GetDomainName(net.request.getUrl()), net.request.getRe_cookies());
			}
		}

		@Override
		public void onErrorResponse(VolleyError error) {
			MQLog.i(TAG, "callback-->error:" + error.getMessage());

			if (!(net.mdialog == null || !net.mdialog.isShowing())) {
				net.mdialog.dismiss();
			}

			if (net.listener != null) {
				net.listener.onAccessComplete(false, null, error, net.flag);
			}

		}
	}

	/**
	 * 图片异步加载
	 * 
	 * @author ping 2014-4-10 上午9:50:02
	 * @param imageview
	 * @param url
	 */
	public static void image(ImageView imageview, String url) {
		image(imageview, url, LOADINGIMAGE, ERRORIMAGE, IMAGEMAXMEASURE);
	}

	public static void image(final ImageView imageview, String url,
			int loadingimg, int errorimg) {
		image(imageview, url, loadingimg, errorimg, IMAGEMAXMEASURE);
	}

	/**
	 * 图片异步加载
	 * 
	 * @author ping
	 * @create 2014-4-17 上午10:33:29
	 * @param imageview
	 * @param url
	 * @param loadingimg
	 *            加载中显示的图片
	 * @param errorimg
	 *            加载错误时显示的图片
	 */
	public static void image(final ImageView imageview, final String url,
			final int loadingimg, final int errorimg, final int maxmeasure) {
		if (imageview == null) {
			return;
		}
		
		if (url == null) {
			imageview.setImageResource(errorimg);
			return;
		}

		checkVar(null);

		imageview.setTag(url);

		final String urlkey = getCacheKey(url, maxmeasure, maxmeasure);
		Bitmap bm = mLruCache.getBitmap(urlkey);
		if (bm == null) {
			imageview.setImageResource(loadingimg);
			//以下4行导致卡顿，所有去掉
//			bm = getCache(url, Bitmap.class);
//			if (bm != null) {
//				mLruCache.putBitmap(urlkey,bm);
//			}
		}

		if (bm == null) {
			mImageLoader.get(url, ImageLoader.getImageListener(imageview,loadingimg, errorimg), maxmeasure, maxmeasure);
		} else {
			final int bmsize = bm.getRowBytes() * bm.getHeight();
			imageview.setImageBitmap(bm);
			mImageRequest = new ImageRequest(url,
					new Response.Listener<Bitmap>() {
						@Override
						public void onResponse(Bitmap bitmap) {
							if (imageview.getTag().toString().equals(url)) {
								imageview.setImageBitmap(bitmap);
								if (bmsize != bitmap.getRowBytes() * bitmap.getHeight()) {
									// 图片改变了，更新图片缓存
									mLruCache.putBitmap(urlkey, bitmap);
								}
							} else if (!bitmap.isRecycled()) {
								bitmap.recycle();
							}
						}
					}, maxmeasure, maxmeasure, Config.ARGB_8888,
					new ErrorListener() {
						@Override
						public void onErrorResponse(VolleyError arg0) {
							imageview.setImageResource(errorimg);
						}
					});
			if (mRequestQueue == null) {
				mRequestQueue = Volley.newRequestQueue(imageview.getContext());
			}
			mRequestQueue.add(mImageRequest);
		}
	}

	/**
	 * 获取参数(得到 ?a=12&b=123)
	 * 
	 * @author ping 2014-4-10 上午9:27:01
	 * @param params
	 * @return
	 */
	public static String getParamStr(Map<String, String> params) {
		StringBuffer bf = new StringBuffer("?");
		if (params != null) {
			Set<String> mset = params.keySet();
			String[] keys = mset.toArray(new String[mset.size()]);

			for (String key : keys) {
				String value = params.get(key);
				if (value == null) {
					params.remove(key);
				} else {
					bf.append(key + "=" + params.get(key) + "&");
				}
			}
		}
		String str = bf.toString();
		return str.substring(0, str.length() - 1);
	}

	public static String getCacheKey(String url, int maxWidth, int maxHeight) {
		return (new StringBuilder(url.length() + 12)).append("#W")
				.append(maxWidth).append("#H").append(maxHeight).append(url)
				.toString();
	}

	public static int calculateSampleSize(BitmapFactory.Options options,
			int reqWidth, int reqHeight) {
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;
		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;
			while ((halfHeight / inSampleSize) >= reqHeight
					&& (halfWidth / inSampleSize) >= reqWidth) {
				inSampleSize *= 2;
			}
		}
		return inSampleSize;
	}
	
	//取得url域名
	public static String GetDomainName(String url) {
		Pattern p = Pattern.compile("^http://[^/]+");
        Matcher matcher = p.matcher(url);
        if(matcher.find()){
        	return  matcher.group();
        }
		return "";
	}

}
