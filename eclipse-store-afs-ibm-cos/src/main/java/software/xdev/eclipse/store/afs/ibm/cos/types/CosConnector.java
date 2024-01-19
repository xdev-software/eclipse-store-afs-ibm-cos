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

package software.xdev.eclipse.store.afs.ibm.cos.types;

import static org.eclipse.serializer.util.X.notNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.serializer.exceptions.IORuntimeException;
import org.eclipse.serializer.io.ByteBufferInputStream;
import org.eclipse.store.afs.blobstore.types.BlobStoreConnector;
import org.eclipse.store.afs.blobstore.types.BlobStorePath;

import com.ibm.cloud.objectstorage.SdkClientException;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.DeleteObjectsRequest;
import com.ibm.cloud.objectstorage.services.s3.model.DeleteObjectsResult;
import com.ibm.cloud.objectstorage.services.s3.model.GetObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Request;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectInputStream;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;


/**
 * Connector for the <a href="https://www.ibm.com/cloud/object-storage">IBM Cloud Object Storage</a>.
 * <p>
 * First create a connection to the <a
 * href="https://cloud.ibm.com/docs/cloud-object-storage?topic=cloud-object-storage-java#java-examples">IBM COS</a>.
 * <pre>
 * AmazonS3 client = ...
 * BlobStoreFileSystem fileSystem = BlobStoreFileSystem.New(
 * 	CosConnector.Caching(client)
 * );
 * </pre>
 */
@SuppressWarnings("checkstyle:MethodName") // MS Naming
public interface CosConnector extends BlobStoreConnector
{
	/**
	 * Pseudo-constructor method which creates a new {@link CosConnector}.
	 *
	 * @param s3 connection to the S3 storage
	 * @return a new {@link CosConnector}
	 */
	static CosConnector New(
		final AmazonS3 s3
	)
	{
		return new CosConnector.Default(
			notNull(s3),
			false
		);
	}
	
	/**
	 * Pseudo-constructor method which creates a new {@link CosConnector} with cache.
	 *
	 * @param s3 connection to the S3 storage
	 * @return a new {@link CosConnector}
	 */
	static CosConnector Caching(
		final AmazonS3 s3
	)
	{
		return new CosConnector.Default(
			notNull(s3),
			true
		);
	}
	
	class Default
		extends BlobStoreConnector.Abstract<S3ObjectSummary>
		implements CosConnector
	{
		public static final int READ_LIMIT = Integer.MAX_VALUE;
		private final AmazonS3 s3;
		
		Default(
			final AmazonS3 s3,
			final boolean useCache
		)
		{
			super(
				S3ObjectSummary::getKey,
				S3ObjectSummary::getSize,
				CosPathValidator.New(),
				useCache
			);
			this.s3 = s3;
		}
		
		@Override
		protected Stream<S3ObjectSummary> blobs(final BlobStorePath file)
		{
			final String prefix = toBlobKeyPrefix(file);
			final Pattern pattern = Pattern.compile(blobKeyRegex(prefix));
			final ListObjectsV2Request request = new ListObjectsV2Request()
				.withBucketName(file.container())
				.withPrefix(prefix);
			return this.s3.listObjectsV2(request)
				.getObjectSummaries()
				.stream()
				.filter(obj -> pattern.matcher(obj.getKey()).matches())
				.sorted(this.blobComparator());
		}
		
		@Override
		protected Stream<String> childKeys(
			final BlobStorePath directory
		)
		{
			final ListObjectsV2Request request = new ListObjectsV2Request()
				.withBucketName(directory.container())
				.withPrefix(toChildKeysPrefix(directory))
				.withDelimiter(BlobStorePath.SEPARATOR);
			return this.s3.listObjectsV2(request)
				.getObjectSummaries()
				.stream()
				.map(S3ObjectSummary::getKey);
		}
		
		@Override
		protected void internalReadBlobData(
			final BlobStorePath file,
			final S3ObjectSummary blob,
			final ByteBuffer targetBuffer,
			final long offset,
			final long length
		)
		{
			final GetObjectRequest request = new GetObjectRequest(file.container(), blob.getKey())
				.withRange(offset, offset + length - 1);
			final S3ObjectInputStream result = this.s3.getObject(request).getObjectContent();
			try
			{
				targetBuffer.put(result.readAllBytes());
			}
			catch(final IOException e)
			{
				throw new SdkClientException(e);
			}
		}
		
		@Override
		protected boolean internalDirectoryExists(
			final BlobStorePath directory
		)
		{
			this.s3.doesObjectExist(directory.container(), toContainerKey(directory));
			return true;
		}
		
		@Override
		protected boolean internalCreateDirectory(
			final BlobStorePath directory
		)
		{
			this.s3.putObject(directory.container(), toContainerKey(directory), "");
			return true;
		}
		
		@Override
		protected boolean internalDeleteBlobs(
			final BlobStorePath file,
			final List<? extends S3ObjectSummary> blobs
		)
		{
			final String[] objects = blobs.stream()
				.map(S3ObjectSummary::getKey)
				.toArray(String[]::new);
			final DeleteObjectsRequest request = new DeleteObjectsRequest(file.container())
				.withKeys(objects);
			final DeleteObjectsResult response = this.s3.deleteObjects(request);
			return response.getDeletedObjects().size() == blobs.size();
		}
		
		@Override
		protected long internalWriteData(
			final BlobStorePath file,
			final Iterable<? extends ByteBuffer> sourceBuffers
		)
		{
			final long nextBlobNumber = this.nextBlobNumber(file);
			final long totalSize = this.totalSize(sourceBuffers);
			
			try(final BufferedInputStream inputStream = new BufferedInputStream(
				ByteBufferInputStream.New(sourceBuffers)
			))
			{
				final int bufferSum = StreamSupport.stream(sourceBuffers.spliterator(), false)
					.mapToInt(Buffer::limit)
					.sum();
				
				final ObjectMetadata objectMetadata = new ObjectMetadata();
				objectMetadata.setContentLength(bufferSum);
				final PutObjectRequest putObjectRequest = new PutObjectRequest(
					file.container(),
					toBlobKey(file, nextBlobNumber),
					inputStream,
					objectMetadata
				);
				putObjectRequest.getRequestClientOptions().setReadLimit(READ_LIMIT);
				
				this.s3.putObject(putObjectRequest);
			}
			catch(final IOException e)
			{
				throw new IORuntimeException(e);
			}
			
			return totalSize;
		}
	}
}
