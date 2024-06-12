[![Latest version](https://img.shields.io/maven-central/v/software.xdev/eclipse-store-afs-ibm-cos?logo=apache%20maven)](https://mvnrepository.com/artifact/software.xdev/eclipse-store-afs-ibm-cos)
[![Build](https://img.shields.io/github/actions/workflow/status/xdev-software/eclipse-store-afs-ibm-cos/check-build.yml?branch=develop)](https://github.com/xdev-software/eclipse-store-afs-ibm-cos/actions/workflows/check-build.yml?query=branch%3Adevelop)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=xdev-software_eclipse-store-afs-ibm-cos&metric=alert_status)](https://sonarcloud.io/dashboard?id=xdev-software_eclipse-store-afs-ibm-cos)

# eclipse-store-afs-ibm-cos

A connector for the [EclipseStore](https://eclipsestore.io/) which allows storing data in the [IBM Cloud Object Storage](https://www.ibm.com/cloud/object-storage).

It uses the [IBM-provided Java SDK](https://github.com/IBM/ibm-cos-sdk-java).

The connector works virtually identical to the [AWS S3 Connector](https://docs.eclipsestore.io/manual/storage/storage-targets/blob-stores/aws-s3.html) of EclipseStore but for IBM COS instead of AWS S3.

To easily handle multiple processes using a single IBM COS instance, we implemented the [SingleAccessManager](eclipse-store-afs-ibm-cos/src/main/java/software/xdev/eclipse/store/afs/ibm/access/SingleAccessManager.java).
It manages access by creating tokens and checking for other tokens. For more information see [the demo application](eclipse-store-afs-ibm-cos-demo/src/main/java/software/xdev/ApplicationWithSingleAccess.java).

## Installation

[Installation guide for the latest release](https://github.com/xdev-software/eclipse-store-afs-ibm-cos/releases/latest#Installation)

## Supported EclipseStore versions

The connector supports [version 1.2.0 of the EclipseStore](https://central.sonatype.com/artifact/org.eclipse.store/storage-embedded/1.2.0).

If you are using a different, not listed version of EclipseStore, this shouldn't be a problem.
Usually you can simply exclude the dependent version of EclipseStore.

## Support

If you need support as soon as possible, and you can't wait for any pull request, feel free to use [our support](https://xdev.software/en/services/support).

## Contributing

See the [contributing guide](./CONTRIBUTING.md) for detailed instructions on how to get started with our project.

## Dependencies and Licenses
View the [license of the current project](LICENSE) or the [summary including all dependencies](https://xdev-software.github.io/eclipse-store-afs-ibm-cos/dependencies)
