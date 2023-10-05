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

import java.util.regex.Pattern;

import org.eclipse.storage.afs.blobstore.types.BlobStorePath;


public interface CosPathValidator extends BlobStorePath.Validator
{
	static CosPathValidator New()
	{
		return new CosPathValidator.Default();
	}
	
	class Default implements CosPathValidator
	{
		Default()
		{
			super();
		}
		
		@Override
		public void validate(
			final BlobStorePath path
		)
		{
			this.validateBucketName(path.container());
		}
		
		/*
		 * https://cloud.ibm.com/docs/cloud-object-storage?topic=cloud-object-storage-compatibility-api-bucket-operations
		 */
		void validateBucketName(
			final String bucketName
		)
		{
			final int length = bucketName.length();
			if(length < 3
				|| length > 63
			)
			{
				throw new IllegalArgumentException(
					"bucket name must be between 3 and 63 characters long"
				);
			}
			if(!Pattern.matches(
				"[a-z0-9\\.\\-]*",
				bucketName
			))
			{
				throw new IllegalArgumentException(
					"bucket name can contain only lowercase letters, numbers, periods (.) and dashes (-)"
				);
			}
			if(!Pattern.matches(
				"[a-z0-9]",
				bucketName.substring(0, 1)
			))
			{
				throw new IllegalArgumentException(
					"bucket name must begin with a lowercase letters or a number"
				);
			}
			if(bucketName.endsWith("-"))
			{
				throw new IllegalArgumentException(
					"bucket name must not end with a dash (-)"
				);
			}
			if(bucketName.contains(".."))
			{
				throw new IllegalArgumentException(
					"bucket name cannot have consecutive periods (..)"
				);
			}
			if(bucketName.contains(".-")
				|| bucketName.contains("-."))
			{
				throw new IllegalArgumentException(
					"bucket name cannot have dashes adjacent to periods (.- or -.)"
				);
			}
			if(Pattern.matches(
				"^((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)$",
				bucketName
			))
			{
				throw new IllegalArgumentException(
					"bucket name must not be in an IP address style"
				);
			}
			if(bucketName.startsWith("xn--"))
			{
				throw new IllegalArgumentException(
					"bucket names must not start with 'xn--'"
				);
			}
		}
	}
}
