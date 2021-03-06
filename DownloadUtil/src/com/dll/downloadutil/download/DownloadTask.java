package com.dll.downloadutil.download;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.util.UUID;

import android.content.Context;

public class DownloadTask implements Runnable {
    protected static final String DEFAULT_FOLDER_PATH = "/downloadutil/";
    protected static final int DEFAULT_TIMEOUT_INTERVAL = 20000;
    protected static final int DEFAULT_MEMORY_CACHE_SIZE = 100;

    protected static final String TAG = DownloadTask.class.getSimpleName();

    protected Context mContext;
    protected boolean mIsDownloading;
    protected DownloadManager mDownloadManager;
    protected String mFileName;
    protected String mFilePath;
    protected String mUUID;
    protected String mTargetURL;
    protected String mTempFilePath;
    protected long mTotalByteLength;
    protected long mLoadedByteLength;
    protected int mDownloadRateLimit;
    protected int mMemoryCacheSize;
    protected int mTag;
    protected int mTimeoutInterval;
    protected Socket mSocket;
    protected FileOutputStream mOutputStream;
    protected File mTargetFile;
    protected ByteArrayOutputStream mByteOutput;
    protected Exception mException;

    protected long mTimeStart; // 测速计时开始时间
    protected long mDeltaLByteLength;
    protected int mSleepTime; // 限速休眠时间
    protected static final int SLEEP_TIME_DELTA = 100;

    /**
     * 获取异常
     * 
     * @return 如果未出现异常则返回null。
     */
    public Exception getException() {
	return mException;
    }

    /**
     * 获取唯一标示
     * 
     * @return
     */
    public String getUUID() {
	if (mUUID == null) {
	    setUUID(UUID.randomUUID().toString());
	}
	return mUUID;
    }

    /**
     * 设置唯一标示
     * 
     * @param UUID
     */
    public void setUUID(String UUID) {
	mUUID = UUID;
    }

    /**
     * 设置文件名，不包括路径
     * 
     * @param fileName
     */
    public void setFileName(String fileName) {
	this.mFileName = fileName;
    }

    /**
     * 获取下载本地文件名
     * 
     * @return 不包括路径。
     */
    public String getFileName() {
	if (mFileName == null) {
	    guessFileName();
	}
	return mFileName;
    }

    public void setFilePath(String filePath) {
	mFilePath = filePath;
    }

    /**
     * 获取本地文件绝对路径
     * 
     * @return 包括文件名
     */
    public String getFilePath() {
	if (mFilePath == null) {
	    setFilePath(FilePathUtil.makeFilePath(mContext, DEFAULT_FOLDER_PATH,
		    getFileName()));
	}
	return mFilePath;
    }

    /**
     * 获取下载临时文件的路径
     * 
     * @return 包括文件名
     */
    public String getTempFilePath() {
	if (mTempFilePath == null) {
	    setTempFilePath(getFilePath() + ".dl");
	}
	return mTempFilePath;
    }

    /**
     * 设置文件存储路径
     * 
     * @param tempFilePath
     *            包括文件名
     */
    public void setTempFilePath(String tempFilePath) {
	mTempFilePath = tempFilePath;
    }

    /**
     * 获取目标文件的总大小
     * 
     * @return 单位：字节
     */
    public long getTotalByteLength() {
	return mTotalByteLength;
    }

    /**
     * 获取当前已经下载的字节数
     * 
     * @return
     */
    public long getLoadedByteLength() {
	return mLoadedByteLength;
    }

    /**
     * 设置下载限速速率
     * 
     * @param downloadRateLimit
     *            单位kps，为0则不限速。
     */
    public void setDownloadRateLimit(int downloadRateLimit) {
	mDownloadRateLimit = downloadRateLimit;
    }

    /**
     * 获取限速
     * 
     * @return 如果不限速则返回0，单位kps
     */
    public int getDownloadRateLimit() {
	return mDownloadRateLimit;
    }

    /**
     * 获取当前内存缓存的大小
     * 
     * @return 单位：kb
     */
    public int getMemoryCacheSize() {
	return mMemoryCacheSize == 0 ? DEFAULT_MEMORY_CACHE_SIZE : mMemoryCacheSize;
    }

    /**
     * 设置缓冲区大小，缓冲区填满将写入文件
     * 
     * @param memoryCacheSize
     */
    public void setMemoryCacheSize(int memoryCacheSize) {
	mMemoryCacheSize = memoryCacheSize;
    }

    /**
     * 设置标签
     * 
     * @param tag
     */
    public void setTag(int tag) {
	mTag = tag;
    }

    /**
     * 获取这个任务的标签
     * 
     * @return
     */
    public int getTag() {
	return mTag;
    }

    /**
     * 获取超时时长
     * 
     * @return 单位：毫秒
     */
    public int getTimeoutInterval() {
	if (mTimeoutInterval == 0) {
	    mTimeoutInterval = DEFAULT_TIMEOUT_INTERVAL;
	}
	return mTimeoutInterval;
    }

    /**
     * 设置超时时长
     * 
     * @param timeoutInterval
     *            单位：毫秒
     */
    public void setTimeoutInterval(int timeoutInterval) {
	mTimeoutInterval = timeoutInterval;
    }

    /**
     * 设置源文件URL
     * 
     * @param targetURL
     */
    public void setTargetURL(String targetURL) {
	mTargetURL = targetURL;
    }

    /**
     * 获取源文件的URL
     * 
     * @return
     */
    public String getTargetURL() {
	return mTargetURL;
    }

    private void guessFileName() {
	if (mFileName != null) {
	    return;
	}
	int lastIndexOfPathComponent = mTargetURL.lastIndexOf("/");
	String last = mTargetURL
		.substring(lastIndexOfPathComponent >= 0 ? lastIndexOfPathComponent + 1
			: 0);
	int loc = last.indexOf("?");
	if (loc >= 0) {
	    last = last.substring(0, loc);
	}
	if (last.length() > 4) {
	    mFileName = last;
	} else {
	    mFileName = mUUID + last;
	}
    }

    /**
     * 实例化一个{@link DownloadTask}
     * 
     * @param context
     *            应用程序上下文
     * @param targetURL
     *            要下载文件的URL
     **/
    public DownloadTask(Context context, String targetURL) {
	mTargetURL = targetURL;
	mContext = context.getApplicationContext();
    }

    @Override
    public void run() {
	if (mDownloadManager != null) {
	    mDownloadManager.onStartDownload(this);
	}
	mException = null;
	InputStream inputStream = null;
	BufferedWriter bufferedWriter = null;
	try {
	    int bufferSize = 8192;
	    if (mDownloadRateLimit > 0 && bufferSize > mDownloadRateLimit << 10) {
		bufferSize = mDownloadRateLimit << 10;
	    }
	    mTargetFile = new File(getTempFilePath());
	    URL url = new URL(getTargetURL());
	    String host = url.getHost();
	    int port = (url.getPort() == -1) ? url.getDefaultPort() : url.getPort();

	    mSocket = new Socket();
	    mSocket.setReceiveBufferSize(bufferSize);
	    mSocket.setSoTimeout(getTimeoutInterval());
	    SocketAddress address = new InetSocketAddress(host, port);
	    mSocket.connect(address, getTimeoutInterval());

	    bufferedWriter = new BufferedWriter(new OutputStreamWriter(
		    mSocket.getOutputStream(), "UTF8"));
	    String requestStr = "GET " + url.getFile() + " HTTP/1.1\r\n";

	    String hostHeader = "Host: " + host + "\r\n";
	    String acceptHeader = "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n";
	    String charsetHeader = "Accept-Charset: GBK,utf-8;q=0.7,*;q=0.3\r\n";
	    String languageHeader = "Accept-Language: zh-CN,zh;q=0.8\r\n";
	    String keepHeader = "Connection: close\r\n";

	    bufferedWriter.write(requestStr);
	    bufferedWriter.write(hostHeader);
	    bufferedWriter.write(acceptHeader);
	    bufferedWriter.write(charsetHeader);
	    bufferedWriter.write(languageHeader);
	    bufferedWriter.write(keepHeader);
	    if (mLoadedByteLength > 0) {
		bufferedWriter.write("Range: bytes=" + mLoadedByteLength + "-\r\n");
	    } else {
		guessFileName();
		String folderPath = getFilePath().substring(0,
			getFilePath().lastIndexOf("/"));
		File folder = new File(folderPath);
		if (!folder.exists() || !folder.isDirectory()) {
		    folder.mkdirs();
		} else {
		    deleteFile(getFilePath());
		    deleteFile(getTempFilePath());
		}
	    }
	    bufferedWriter.write("\r\n");
	    bufferedWriter.flush();
	    inputStream = mSocket.getInputStream();
	    HttpResponseHeaderParser responseHeader = new HttpResponseHeaderParser();
	    String responseHeaderLine = null;
	    char readChar = 0;
	    StringBuilder headerBuilder = new StringBuilder();
	    while ((byte) (readChar = (char) inputStream.read()) != -1) {
		headerBuilder.append(readChar);
		if (readChar == 10) {
		    responseHeaderLine = headerBuilder.substring(0,
			    headerBuilder.length() - 2);
		    headerBuilder.setLength(0);
		    if (responseHeaderLine.length() == 0) {
			break;
		    } else {
			responseHeader.addResponseHeaderLine(responseHeaderLine);
		    }
		}
	    }

	    if (mTotalByteLength == 0) {
		mTotalByteLength = responseHeader.getContentLength();
	    }
	    mOutputStream = new FileOutputStream(mTargetFile, true);
	    mByteOutput = new ByteArrayOutputStream();
	    byte[] buffer = new byte[bufferSize];
	    int length = -1;
	    mTimeStart = System.currentTimeMillis();
	    mDeltaLByteLength = 0;
	    mSleepTime = 0;
	    while ((length = inputStream.read(buffer)) != -1 && mIsDownloading) {
		mByteOutput.write(buffer, 0, length);
		if (mByteOutput.size() >= getMemoryCacheSize() << 10) {
		    writeCache();
		}
		limitTheByteRate(length);
	    }

	} catch (Exception e) {
	    mException = e;
	} finally {
	    if (mException != null && mIsDownloading && mDownloadManager != null) {
		mDownloadManager.onFailedDownload(this);
	    }
	    try {
		if (bufferedWriter != null) {
		    bufferedWriter.close();
		    bufferedWriter = null;
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    try {
		if (inputStream != null) {
		    inputStream.close();
		    inputStream = null;
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    writeCache();
	    try {
		if (mOutputStream != null) {
		    mOutputStream.close();
		    mOutputStream = null;
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    try {
		if (mByteOutput != null) {
		    mByteOutput.close();
		    mByteOutput = null;
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }

	    if (mLoadedByteLength >= mTotalByteLength) {
		mTargetFile.renameTo(new File(getFilePath()));
		new File(getConfigFilePath()).delete();
		if (mException == null && mDownloadManager != null) {
		    mDownloadManager.onFinishDownload(this);
		}
		return;
	    }
	    stop();
	}
    }

    protected void limitTheByteRate(int receiveByteLength) {
	if (!(mDownloadRateLimit > 0)) {
	    return;
	}
	long currentTime = System.currentTimeMillis();
	long passedTime = currentTime - mTimeStart;
	mDeltaLByteLength += receiveByteLength;
	if (passedTime > 0) {
	    int byteRate = (int) (mDeltaLByteLength * 1000 / passedTime);
	    if (passedTime >= 5000) {
		mTimeStart = currentTime;
		mDeltaLByteLength = 0;
	    }
	    if (byteRate >= mDownloadRateLimit << 10) {
		mSleepTime += SLEEP_TIME_DELTA;
	    } else {
		if (mSleepTime >= SLEEP_TIME_DELTA) {
		    mSleepTime -= SLEEP_TIME_DELTA;
		}
	    }
	    try {
		Thread.sleep(mSleepTime);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
    }

    private void writeCache() {
	if (!mTargetFile.exists()) {
	    try {
		mTargetFile.createNewFile();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}
	if (mByteOutput != null && mByteOutput.size() > 0 && mOutputStream != null) {
	    try {
		mByteOutput.writeTo(mOutputStream);
		mLoadedByteLength += mByteOutput.size();
		mByteOutput.reset();
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	}
	writeConfigFile();
	if (mDownloadManager != null) {
	    mDownloadManager.onReceiveDownloadData(this);
	}
    }

    public synchronized void writeConfigFile() {
	ObjectOutputStream out = null;
	try {
	    out = new ObjectOutputStream(new FileOutputStream(getConfigFilePath()));
	    out.writeObject(getConfig());
	} catch (FileNotFoundException e) {
	    e.printStackTrace();
	} catch (IOException e) {
	    e.printStackTrace();
	} finally {
	    if (out != null) {
		try {
		    out.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}
	    }
	}
    }

    protected void deleteFile(String filePath) {
	File file = new File(filePath);
	if (file.exists() && file.isFile()) {
	    file.delete();
	}
    }

    /**
     * 开始这个下载任务
     */
    public synchronized void start() {
	if (mIsDownloading
		|| (mLoadedByteLength >= mTotalByteLength && mTotalByteLength != 0)) {
	    return;
	}
	mIsDownloading = true;
	Thread thread = new Thread(this);
	thread.setPriority(Thread.MIN_PRIORITY);
	thread.start();
    }

    public void setDownloadManager(DownloadManager manager) {
	this.mDownloadManager = manager;
    }

    /**
     * 任务当前是否正在运行
     * 
     * @return
     */
    public boolean isDownloading() {
	return mIsDownloading;
    }

    /**
     * 生成当前下载状态的JSON对象
     * 
     * @return
     */
    private DownloadConfig getConfig() {
	DownloadConfig cfg = new DownloadConfig();
	cfg.uuid = getUUID();
	cfg.targetURL = getTargetURL();
	cfg.loadedByteLength = getLoadedByteLength();
	cfg.totalByteLength = getTotalByteLength();
	cfg.downloadRateLimit = getDownloadRateLimit();
	cfg.memoryCacheSize = getMemoryCacheSize();
	cfg.tag = getTag();
	cfg.filePath = getFilePath();
	cfg.fileName = getFileName();
	return cfg;
    }

    public static DownloadTask createTaskFromConfigFile(Context context, String configFile) {
	File config = new File(configFile);
	try {
	    return new DownloadTask(context, config);
	} catch (Exception e) {
	    e.printStackTrace();
	}
	return null;
    }

    private DownloadTask(Context context) {
	mContext = context.getApplicationContext();
    }

    /**
     * 通过JSON对象实例化一个{@link DownloadTask}。
     * 
     * @param context
     * @param jsonObject
     */
    private DownloadTask(Context context, File configFile) throws Exception {
	this(context);
	ObjectInputStream in = null;
	try {
	    in = new ObjectInputStream(new FileInputStream(configFile));
	    DownloadConfig cfg = (DownloadConfig) in.readObject();
	    mUUID = cfg.uuid;
	    mTargetURL = cfg.targetURL;
	    mLoadedByteLength = cfg.loadedByteLength;
	    mTotalByteLength = cfg.totalByteLength;
	    mDownloadRateLimit = cfg.downloadRateLimit;
	    mMemoryCacheSize = cfg.memoryCacheSize;
	    mTag = cfg.tag;
	    mFilePath = cfg.filePath;
	    mFileName = cfg.fileName;

	    File downloadFile = new File(mFilePath);
	    if (downloadFile.length() != mLoadedByteLength) {
		mLoadedByteLength = 0;
		mTotalByteLength = 0;
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	    throw e;
	} finally {
	    if (in != null) {
		try {
		    in.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}
	    }
	}
    }

    /**
     * 获取当前下载的进度
     * 
     * @return 从0到1.
     */
    public float getProgress() {
	return mTotalByteLength > 0 ? ((float) mLoadedByteLength) / mTotalByteLength : 0;
    }

    /**
     * 停止这个下载任务。
     */
    public synchronized void stop() {
	if (!mIsDownloading) {
	    return;
	}
	try {
	    if (mSocket != null && !mSocket.isClosed()) {
		mSocket.close();
		mSocket = null;
	    }
	} catch (IOException e) {
	    e.printStackTrace();
	}
	mIsDownloading = false;
    }

    public String getConfigFilePath() {
	return getFilePath() + ".dlcfg";
    }

    private static class DownloadConfig implements Serializable {
	private static final long serialVersionUID = -4946894359380266539L;
	public String uuid;
	public String targetURL;
	public long loadedByteLength;
	public long totalByteLength;
	public int downloadRateLimit;
	public int memoryCacheSize;
	public int tag;
	public String filePath;
	public String fileName;
    }
}
