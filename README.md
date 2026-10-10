# Multi-threaded File Download (Client/Server)

A TCP client and server that download a file in 10 parts at the same time by default (the number of workers can be changed). Each part is requested by its own worker thread over its own connection, and written straight into the final file at the correct position. The project supports two ways of moving bytes, so they can be compared:

| Mode | How bytes move |
|---|---|
| `traditional` | Java loop: `read` into a 64 KB `byte[]`, then `write` |
| `nio` | `FileChannel.transferTo` (server) and `FileChannel.transferFrom` (client) |

---

## 1. Project layout

```
Assignment2/
├── File_Container/
│   ├── shared/                  <- files the server offers (put your test files here)
│   └── downloaded_file/         <- where the client saves downloads (created automatically)
├── MultithreadFileDownload/
│   └── src/
│       ├── Server.java
│       ├── Client.java
│       └── TestServerSide.java  <- small program to test the server by hand
├── .gitignore                   <- files and folders Git should not track
├── Assignment_Multithread_File_Download.pdf   <- the assignment description
├── ProjectTracking.md           <- notes tracking the progress of the project
└── README.md                    <- this file: how to run and use the project
```

| File | Purpose |
|---|---|
| `Server.java` | Listens on port 5050, handles many clients at once, answers `LIST`, `INFO` and `GET` |
| `Client.java` | Splits a file into ranges, runs the workers, saves the file, checks size and SHA-256 |
| `TestServerSide.java` | Sends `LIST`, `INFO` and `GET` requests and prints the replies |

---

## 2. Requirements

- JDK 11 or newer (check with `java -version`)
- Free port **5050**
- Free disk space of about twice the size of the file you download (the original plus the copy)

---

## 3. Setup

1. Put the files to share into `File_Container/shared/` (for example `test.txt`, and one large file such as `BigFile.zip` for the experiments).
2. Avoid spaces in file names. The protocol splits requests on spaces, so `my notes.txt` would not work.
3. Check the `SHARED_DIR` constant in `Server.java`. It is a path **relative to the folder you run `java` from**. If you run from `src`, it must point to `File_Container/shared` from there. The server prints the full path it uses when it starts, so check that line.

---

## 4. Compile

From the `src` folder:

```
javac Server.java Client.java TestServerSide.java
```

---

## 5. Run

Use two terminals, both in the `src` folder.

### Terminal 1: the server

```
java Server              # traditional mode (default)
java Server nio          # NIO mode
```

Expected output:

```
Mode: traditional
Server listening on port 5050
```

### Terminal 2: the client

```
java Client LIST
java Client <file> [workers] [traditional | nio] [original path]
```

| Argument | Meaning | Default |
|---|---|---|
| `LIST` | Shows the files on the server and exits, with no download |  |
| `<file>` | Name of a file in the server's shared folder | |
| `[workers]` | Number of workers | 10 |
| `[mode]` | `traditional` or `nio` | `traditional` |
| `<original path>` | Path to the original file, used only to compare hashes | none (hash is printed but not compared) |



The arguments are positional. To choose a mode you must also give the worker count, and to give the original path you must give the worker count and the mode too (for example `10 nio <path>`). Running `java Client` with no arguments does the same as `java Client list`.

### Examples

```
# list the files on the server (no download)
java Client list

# 10 workers, traditional (default)
java Client BigFile.zip

# 1 worker, traditional
java Client BigFile.zip 1

# 10 workers, NIO
java Client BigFile.zip 10 nio

# 1 worker, NIO
java Client BigFile.zip 1 nio

# 10 workers, NIO, and compare the hash with the original
java Client BigFile.zip 10 nio ../../File_Container/shared/BigFile.zip
```

**Important:** 
>start the server and the client in the **same mode**. Mixed combinations still work (the protocol is identical), but they do not measure either path properly.
>**To use NIO you must give the worker count first** (`10 nio`, not just `nio`). The word in the worker position is read as a number, so a mode typed there is not used as the mode.

### Example client output

```
java Client list

FILE list:
BigFile.zip (856817495 bytes)
Big_2_Test.zip (2524242124 bytes)
examples_of_os.png (16466 bytes)
test.txt (976 bytes)
```
```
java Client test.txt 5 nio ../../File_Container/shared/test.txt

Mode: nio
Size of test.txt = 976 bytes
Worker 4 finished!
Worker 2 finished!
Worker 1 finished!
Worker 3 finished!
Worker 0 finished!
Downloaded in 0.013 s (0.07 MB/s)
Size check: OK
Hash check: OK (identical to original)
```

What each part means:

- **File list:** shown only by java Client LIST, as the reply to LIST with exact sizes in bytes.
- **Ranges verified:** the client checked that the ranges have no gaps and no overlaps and add up to the file size.
- **Worker N finished:** workers finish in a different order every run. That is normal, because each one writes into its own region of the file.
- **Downloaded in ...:** time covers only the download. The hash check runs afterwards and is not included.
- **Size check / Hash check:** the size check alone is weak (the output file is created at full size before the download starts). **A run only counts if the hash check says OK.**

---

## 6. How it works

### Protocol

Every request is one text line ending in `\n`. The server answers and closes the connection, so each worker uses one connection for one request.

| Request | Success reply | Error reply |
|---|---|---|
| `LIST` | One `FILE <name> <size>` line per file, then `END` | |
| `INFO <filename>` | `SIZE <bytes>` | `ERROR <code> <message>` |
| `GET <filename> <offset> <length>` | `OK <length>`, then exactly that many raw bytes | `ERROR <code> <message>` |

Why `GET` sends `OK <length>` first: the payload is raw binary, so a newline cannot mark its end. The client reads one header line, learns the exact byte count, then reads exactly that many bytes. The first word also tells it whether bytes follow (`OK`) or not (`ERROR`).

Error codes used: `400` for a malformed request or invalid range, `404` for a file that does not exist.

### Server

- The main thread only calls `accept()`. Each connection is handed to a fixed pool of 20 threads, so many clients are served at once.
- `GET` validates the request, sends the header, then sends exactly the requested slice.
- `traditional`: `RandomAccessFile.seek(offset)`, then a loop that reads 64 KB and writes it to the socket.
- `nio`: clients are accepted with `ServerSocketChannel`, and the slice is sent with `FileChannel.transferTo` in a loop (it may move fewer bytes than asked, so it repeats until the range is done).

### Client

1. Sends `INFO` to get the exact file size.
2. Calculates the ranges: `chunk = size / workers`. Worker `i` starts at `i * chunk`. The **last worker takes the remainder**.
3. Creates the output file at its full size, so every worker has a place to write.
4. Starts one worker thread per range. Each opens its own connection and its own file handle, sends one `GET`, and writes its bytes at its own offset (`seek` + `write`, or `transferFrom` with a position).
5. Waits for all workers. If any worker fails, the whole download fails.
6. Checks the file size and the SHA-256 hash.

There are no part files and no merge step. The file is complete when the last worker finishes.

---

## 7. Testing the server by hand

With the server running:

```
java TestServerSide
```

It sends `LIST`, `INFO test.txt` and several `GET` requests (including invalid ones). Check that:

- successful `GET` requests print `Expected N bytes, received N` and `No extra bytes after payload (good)`
- invalid requests print an `ERROR ...` header and no payload

---

## 8. Running the experiments

For each combination, run **at least 3 times** and record the time, throughput, and whether the hash check passed.

| Mode | Workers | Run | Time (s) | MB/s | Hash OK? |
|---|---|---|---|---|---|
| traditional | 1 | 1 | | | |
| traditional | 1 | 2 | | | |
| traditional | 1 | 3 | | | |
| traditional | 10 | 1 | | | |
| traditional | 10 | 2 | | | |
| traditional | 10 | 3 | | | |
| nio | 1 | 1 | | | |
| nio | 1 | 2 | | | |
| nio | 1 | 3 | | | |
| nio | 10 | 1 | | | |
| nio | 10 | 2 | | | |
| nio | 10 | 3 | | | |

Tips for fair results:

- Use the **same file** and the **same computer** for every run.
- Start the server in the matching mode, and restart it when you switch modes.
- Ignore a time if the hash check did not pass. A failed run can look very fast because no real data was moved.
- Throughput here is `size / 1,048,576 / seconds`, so 1 MB means 1,048,576 bytes.

### Limits to mention when running on `localhost`

- **Loopback network:** data moves between the two programs through memory, with no real network, so speeds are much higher than a real link.
- **Page cache:** the operating system keeps recently read file data in RAM, so repeated runs may read from memory instead of disk.
- **Write cache and storage cache:** writes may land in memory first and reach the disk later, so the timer may not include the real disk write.
- **Shared resources:** server and client share the same CPU and memory.
- **Why 10 workers are not 10 times faster:** the workers share the same disk, CPU and loopback path, and the download ends only when the slowest worker finishes.
- **Why NIO does not always win:** `transferTo` mainly saves a copy on the server side; on one machine with cached data there is little to save. The benefit also depends on the JDK version and operating system.

---

## 9. Troubleshooting

| Problem | Likely cause and fix |
|---|---|
| `Connection refused` | The server is not running, or it is not on port 5050 |
| `Address already in use` | An older server is still running. Stop it, then start again |
| `SHARED_DIR` folder not found | The path is relative to where you run `java`. Fix the constant or run from the right folder |
| `Hash check: MISMATCH` | A worker wrote wrong or no data. Check for typos in the `transferFrom` or `read`/`write` code. Do not use that run's time |
| `Download failed: ... Server respond: ERROR ...` | The server rejected a request. The message says why (bad range, file not found) |
| Large file (over 2 GB) fails with 1 worker | `length` must be a `long` on the server, not an `int` |
| Server stops answering new clients | Its thread pool is full of idle connections (for example from a test left open). Close the test client or restart the server |
| Ran with `nio` need to specify `workers` positionally| The mode was typed in the worker-count position (for example `java Client BigFile.zip nio`). Put the worker count first: `java Client BigFile.zip 10 nio` |
| Output file exists with the right size but is wrong | A failed run leaves a zero-filled file. Only a passing hash check proves a download worked |

---

## 10. Where files are saved

Downloads are saved by the client in `File_Container/downloaded_file/`, with the prefix `downdloaded_` (the spelling comes from the `outputPath` line in `Client.java`; change it there if you like). The folder is created automatically, and the old file is deleted at the start of each run.

Large test files take real disk space, so delete old downloads between experiments.

---

## 11. Notes

- The server has no password. While it runs, anyone who can reach port 5050 can list and download everything in the shared folder.
- The client connects to `localhost`. To use another computer, change the `HOST` constant in `Client.java` to the server's IP address and allow port 5050 through the firewall.
