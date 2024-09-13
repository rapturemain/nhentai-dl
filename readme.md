# Porn Downloader Enterprise Edition
A simple Kotlin CLI program to download doujinshi from [nhentai.net](https://nhentai.net/).

## Usage
CLI usage example. 
In this example, the doujinshi with IDs listed in `ids.txt` will be downloaded and saved in a zip archives (per doujinshi)
located in the current directory.
```shell
java -jar nhentai-dl.jar -ids ids.txt -f zip_archive -ua "YOUR_USER_AGENT" -ck "YOUR_COOKIES" 
```
- `ids.txt` is a file containing the IDs of the doujinshi you want to download, one per line.
- Replace `"YOUR_USER_AGENT"` and `"YOUR_COOKIES"` with your actual user agent and cookies, respectively.

Other available options can be viewed by running:
```shell
java -jar nhentai-dl.jar -h
```

Java 21 is required to run this program.

## Motivation

Inspired by [RicterZ's nhentai project](https://github.com/RicterZ/nhentai), 
I decided to create my own implementation from scratch. While the original project addresses the same goals, 
I identified a few areas where performance and stability could be improved:

1. High CPU usage due to the use of Python multiprocessing.
2. Slower download speeds as a result of sequential downloading of each title and the overhead of subprocess creation.
3. Occasional corrupted images in the downloaded doujinshi (approximately 10% titles affected).
4. (!!!) My clicky external HDD doesn't like the constant read/write operations. 

To overcome these limitations, I developed this project in Kotlin, focusing on improved efficiency, 
parallel processing, and image integrity.
