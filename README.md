# Simple BitTorrent Reimplementation

A from-scratch Java implementation of the core BitTorrent protocol, built for the Computer Networks course (HCMUT, semester 241). It includes an HTTP tracker, `.torrent` file generation and parsing, and a peer-to-peer client that downloads files piece by piece from multiple peers at once.

No external libraries are used — only the JDK.

## Features

- **Tracker server** — HTTP `/announce` endpoint that keeps a list of peers per torrent (keyed by info hash), handles the `stopped` event, and returns bencoded peer lists with seeder/leecher counts.
- **Bencoding** — encoder and decoder for the BitTorrent serialization format.
- **Torrent files** — generate a `.torrent` from any file (256 KB pieces, SHA-1 piece hashes) and parse existing ones.
- **Peer wire protocol** — handshake, bitfield, have, interested/not interested, choke/unchoke, request and piece messages.
- **Downloading**
  - Each piece is split into 16 KB blocks, with up to 5 requests pipelined per peer.
  - Pieces are picked **rarest-first**.
  - Every completed piece is checked against its SHA-1 hash.
- **Choking** — every 10 s the peers with the best download rate are unchoked, plus one optimistic unchoke on a 30 s cycle.
- **Multiple torrents per client** — each torrent runs its own peer server on its own port.

## Project structure

```
code/
├── Main.java              # Interactive CLI (download / upload)
├── tracker/
│   ├── TrackerServer.java # HTTP tracker on port 8080
│   ├── TorrentClient.java # Client: talks to the tracker, manages torrents, piece selection, choking
│   └── Peer.java          # Peer record kept by the tracker
├── peer/
│   ├── PeerServer.java    # Accepts incoming peer connections
│   ├── PeerConnection.java# One connection to a remote peer (message loop, requests, speed tracking)
│   ├── Handshake.java     # BitTorrent handshake
│   ├── PeerProtocol.java  # Message IDs and constants
│   └── FileManager.java   # Piece/block buffers, hash validation, writing to disk
├── torrent/
│   └── TorrentInfo.java   # Parse and generate .torrent files
├── util/
│   ├── BEncode.java / BDecode.java
│   ├── SHA.java           # SHA-1 helpers
│   ├── Message.java       # Peer message builders
│   ├── URLHandle.java     # URL encoding of binary values
│   └── Conf.java          # Tunable constants (ports, piece size, choking intervals)
└── testDir/               # Sample files to share
```

## Requirements

- JDK 8 or newer

## Build

Run these from the `code/` directory (the demos use paths relative to it):

```sh
cd code
javac -d out $(find . -name "*.java")
```

## Run

### 1. Start the tracker

```sh
java -cp out tracker.TrackerServer
```

The tracker listens on `http://localhost:8080/announce`. Press **Enter** to stop it.

### 2a. Interactive client

In another terminal:

```sh
java -cp out Main
```

- `1` — **download**: enter a `.torrent` path, an output file path, and a port for this peer.
- `2` — **upload (seed)**: enter the file to share, where to write the generated `.torrent`, and a port.
- `exit` — quit.

Every peer needs its own port.

### 2b. Multi-peer demo

`TorrentClient` has a built-in demo with one seeder and five leechers (ports 6880–6885) in a single process:

```sh
java -cp out tracker.TorrentClient
```

It expects a video at `testDir/dieubuonnhat.mp4`. Media files are not tracked by git, so put any file at that path, or change the path in `TorrentClient.main`. The leechers write their copies to `testDir/video1.mp4` … `testDir/video5.mp4`.

## Configuration

Constants live in `code/util/Conf.java`:

| Constant | Default | Meaning |
|---|---|---|
| `SERVER_PORT` | `8080` | Tracker port |
| `pieceLength` | 256 KB | Piece size for generated torrents |
| `BLOCK_LENGTH` | 16 KB | Size of each block request |
| `MAX_PIPELINE` | 5 | Outstanding requests per peer |
| `MAX_UNCHOKED_PEERS` | 2 | Peers unchoked by download rate |
| `UNCHOKE_INTERVAL` | 10 s | How often choking is recomputed |
| `OPTIMISTIC_UNCHOKE_INTERVAL` | 30 s | Optimistic unchoke cycle |

## Limitations

- Only single-file torrents.
- The tracker URL is fixed to `localhost:8080`, so everything is meant to run on one machine.
- The whole file is kept in memory while downloading.
- No DHT, no peer exchange, no encryption.
