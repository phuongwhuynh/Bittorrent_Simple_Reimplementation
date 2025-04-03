# Simple BitTorrent Reimplementation 

This project is a simplified reimplementation of the BitTorrent protocol, focusing on the backend components. It includes a **tracker server** responsible for managing peer connections and **torrent clients** that communicate with the tracker to simulate basic peer-to-peer file sharing functionality.

## Getting Started

### Starting the Tracker Server

To start the tracker server, compile and run the following command:

```sh
javac tracker/TrackerServer.java
java tracker.TrackerServer
```

### Running the Torrent Client

To run a test torrent client, compile and execute the following commands:

```sh
javac tracker/TorrentClient.java
java tracker.TorrentClient
```
