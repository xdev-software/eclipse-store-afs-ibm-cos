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

import java.util.ArrayList;
import java.util.List;

import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.iterable.S3Objects;
import com.ibm.cloud.objectstorage.services.s3.model.AmazonS3Exception;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;


public interface CosAccessCommunicator
{
	List<S3ObjectSummary> getExistingFilesWithPrefix();
	
	boolean checkIfFileExists(final String fileName);
	
	void createEmptyFile(final String fileName);
	
	void deleteFile(final String fileName);
	
	public class Default implements CosAccessCommunicator
	{
		
		private final AmazonS3 client;
		private final AccessConfiguration configuration;
		
		public Default(final AccessConfiguration configuration, final AmazonS3 client)
		{
			this.client = client;
			this.configuration = configuration;
		}
		
		@Override
		public List<S3ObjectSummary> getExistingFilesWithPrefix()
		{
			final ArrayList<S3ObjectSummary> existingFilesWithPrefix = new ArrayList<>();
			S3Objects
				.withPrefix(this.client, this.configuration.getBucketName(), this.configuration.getAccessFilePrefix())
				.forEach(existingFilesWithPrefix::add);
			return existingFilesWithPrefix;
		}
		
		@Override
		public boolean checkIfFileExists(final String fileName)
		{
			try
			{
				return this.client.doesObjectExist(this.configuration.getBucketName(), fileName);
			}
			catch(final AmazonS3Exception e)
			{
				// This is a "feature": https://github.com/aws/aws-sdk-java/issues/974#issuecomment-272634444
				if(e.getStatusCode() == 403)
				{
					return false;
				}
				throw e;
			}
		}
		
		@Override
		public void createEmptyFile(final String fileName)
		{
			this.client.putObject(
				this.configuration.getBucketName(),
				fileName,
				""
			);
		}
		
		@Override
		public void deleteFile(final String fileName)
		{
			this.client.deleteObject(
				this.configuration.getBucketName(),
				fileName
			);
		}
	}
}
