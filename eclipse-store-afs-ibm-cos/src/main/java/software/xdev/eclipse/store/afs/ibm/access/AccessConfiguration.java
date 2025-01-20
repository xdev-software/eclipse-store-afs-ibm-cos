/*
 * Copyright © 2023 XDEV Software (https://xdev.software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package software.xdev.eclipse.store.afs.ibm.access;

import java.util.concurrent.TimeUnit;


/**
 * Holds all the necessary configuration for the {@link SingleAccessManager}.
 */
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
