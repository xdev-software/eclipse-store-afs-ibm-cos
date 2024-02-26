package software.xdev.eclipse.store.afs.ibm.access;

import java.util.concurrent.TimeUnit;


public class AccessConfiguration
{
	public static final String DEFAULT_ACCESS_FILE_PREFIX = "ACCESS_FILE_";
	public static final long DEFAULT_CHECK_INTERVAL_FOR_SINGLE_ACCESS = TimeUnit.SECONDS.toMillis(1);
	public static final long DEFAULT_CHECK_INTERVAL_FOR_TERMINATE_ACCESS = TimeUnit.SECONDS.toMillis(1);
	public static final long DEFAULT_KEEP_ALIVE_INTERVAL_FOR_TOKEN = TimeUnit.SECONDS.toMillis(1);
	private String accessFilePrefix = DEFAULT_ACCESS_FILE_PREFIX;
	private final String bucketName;
	
	private long checkIntervalForSingleAccess = DEFAULT_CHECK_INTERVAL_FOR_SINGLE_ACCESS;
	private long checkIntervalForTerminateAccess = DEFAULT_CHECK_INTERVAL_FOR_TERMINATE_ACCESS;
	private long keepAliveIntervalForToken = DEFAULT_KEEP_ALIVE_INTERVAL_FOR_TOKEN;
	
	public AccessConfiguration(
		final String accessFilePrefix,
		final String bucketName,
		final long checkIntervalForSingleAccess,
		final long checkIntervalForTerminateAccess,
		final long keepAliveIntervalForToken)
	{
		this.accessFilePrefix = accessFilePrefix;
		this.bucketName = bucketName;
		this.checkIntervalForSingleAccess = checkIntervalForSingleAccess;
		this.checkIntervalForTerminateAccess = checkIntervalForTerminateAccess;
		this.keepAliveIntervalForToken = keepAliveIntervalForToken;
	}
	
	public AccessConfiguration(final String bucketName)
	{
		this.bucketName = bucketName;
	}
	
	public String getAccessFilePrefix()
	{
		return this.accessFilePrefix;
	}
	
	public String getBucketName()
	{
		return this.bucketName;
	}
	
	public long getCheckIntervalForSingleAccess()
	{
		return this.checkIntervalForSingleAccess;
	}
	
	public long getCheckIntervalForTerminateAccess()
	{
		return this.checkIntervalForTerminateAccess;
	}
	
	public long getKeepAliveIntervalForToken()
	{
		return this.keepAliveIntervalForToken;
	}
}
