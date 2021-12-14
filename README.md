# Block Metadata [![Java CI with Gradle](https://github.com/itsMatoosh/block-metadata/actions/workflows/gradle.yml/badge.svg)](https://github.com/itsMatoosh/block-metadata/actions/workflows/gradle.yml)
A library for Minecraft plugins, allowing for efficient storage of persistent block metadata.
Store metadata for a very large number of blocks without performance overhead.
Fully asynchronous and multi-threaded for maximum performance.
## Getting started
To get started, add the Block Metadata library to your plugin. You can do this easily using either Maven or Gradle.
### Adding Block Metadata by Gradle
Modify your build.gradle file to include the following:
```groovy
repositories {
	maven { url 'https://jitpack.io' }
}

dependencies {
	implementation 'com.github.itsMatoosh:block-metadata:1.3.1'
}
```
### Adding Block Metadata by Maven
Modify your pom.xml file to include the following:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.itsMatoosh</groupId>
    <artifactId>block-metadata</artifactId>
    <version>1.3.1</version>
</dependency>
```

## Usage
In order to use Block Metadata, instantiate a BlockMetadataStorage for each type of metadata you would like to store.

Here, we will instantiate a BlockMetadataStorage that can be used to store String metadata for each block.
```java
public class YourPlugin extends JavaPlugin {

    private BlockMetadataStorage<String> metadataStorage;

    @Override
    public void onEnable() {
        // get the directory to save the metadata data in
        Path dataDir = this.getDataDirectory().toPath().resolve("data");
    
        // create the metadata store
        metadataStorage = new BlockMetadataStorage<>(this, dataDir)       
    }
}
```
### Setting block metadata
We can use the instantiated BlockMetadataStorage to store metadata on blocks. Metadata can only be stored on block that are in currently loaded chunks.
```java
Block block = ...
BlockMetadataStorage<String> metadataStorage = ...
metadataStorage.setMetadata(block, "abcd");
```
### Fetching block metadata
We can use the instantiated BlockMetadataStorage to fetch metadata from blocks.
```java
Block block = ...
BlockMetadataStorage<String> metadataStorage = ...
String data = metadataStorage.getMetadata(block);
```
### Clearing block metadata
We can use the instantiated BlockMetadataStorage to clear metadata from blocks.
```java
Block block = ...
BlockMetadataStorage<String> metadataStorage = ...
String data = metadataStorage.removeMetadata(block);
```
