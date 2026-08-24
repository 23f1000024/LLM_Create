# Networking Architecture & Honest Limitations

This document explains exactly how UNO P2P connects phones **without any
dedicated signaling server or game server**, what works, what does not, and why.
It is deliberately blunt about the limits.

## The one-sentence summary

One player's phone (the **host**) runs a tiny embedded HTTP/WebSocket service
*only* long enough to introduce the phones to each other. After that, all game
data and all voice run **directly peer-to-peer over WebRTC**. There is no cloud
in the loop, and none is secretly added.

## Roles

- **Host phone** — runs the game (authoritative `GameEngine`) **and** a temporary
  local signaling service. It is just another player's phone, not a server you
  deploy.
- **Joining phones (clients)** — connect to the host's signaling service to
  exchange connection info, then hold direct WebRTC links to every other player.

## How host signaling works

The host starts an embedded **Ktor (CIO) HTTP + WebSocket** service on the LAN
(default port `8080`). It exposes only:

| Endpoint            | Purpose                                             |
|---------------------|-----------------------------------------------------|
| `GET  /room/info`   | Room metadata (code, host name, player count)       |
| `POST /room/join`   | Obtain a seat: returns `playerId` + a session token |
| `WS   /signal`      | Authenticate, then relay WebRTC SDP/ICE to peers     |

The signaling service **contains no game logic**. Its entire job is:

1. discovery/join,
2. relaying **SDP offers/answers** and **ICE candidates** between peers,
3. announcing who is in the room.

Once two peers have exchanged SDP/ICE and their WebRTC connection is up, the
signaling channel goes quiet — it carries **no** game moves and **no** audio.

```
   Joining phone                 Host phone
   ------------                  ----------
   POST /room/join  ───────────▶ admit → {playerId, token}
   WS  /signal  (hello+token) ─▶ authenticated
        offer ────────────────▶ relayed to target peer
        ◀──────────────── answer
        candidate ⇄ candidate  (trickle ICE)
   ── WebRTC DTLS/SRTP established ──
        game DataChannel + voice audio  ⇄  DIRECT, peer-to-peer
```

## How WebRTC is used

- **Game messages** travel on an **ordered, reliable DataChannel** as JSON
  (`GameMessage`). Easy to debug now; the wire format is isolated so it can be
  swapped later.
- **Voice** travels as **WebRTC audio tracks** in a **mesh** — every player has a
  direct audio connection to every other player.
- All WebRTC media/data is **DTLS/SRTP encrypted** end to end, even on the LAN.

Glare is avoided with a deterministic rule: of any two peers, the one whose
`playerId` sorts lower creates the offer and the DataChannel.

## Host authority (why the host validates everything)

The host holds the only true `GameState`. Clients send **requests**
(`PLAY_CARD`, `DRAW_CARD`, …), each with a `requestId`. The host validates turn,
card ownership, and legality via the `GameEngine`, then broadcasts a
**redacted** snapshot to each client — you receive **your** hand and only the
**counts** of everyone else's. A malicious or buggy client cannot "play a card
it doesn't have"; the host rejects it and never leaks private hands.

## What works

- **Same Wi-Fi LAN (the primary, supported mode).** All phones on one Wi-Fi/
  hotspot connect directly with no STUN/TURN, using host/local ICE candidates.
  Create a room, share the QR/address, others join, play + talk. No Internet
  needed.

## What is *not* guaranteed (read this)

- **Arbitrary phones across the public Internet may not be able to connect
  directly.** This is a property of the Internet, not a bug we can wish away.
  Direct peer-to-peer between two phones on different networks typically needs
  one of:
  - a **publicly reachable IPv6** address on the host, or
  - **router port-forwarding** to the host, or
  - **compatible NAT** conditions on both sides, or
  - firewall permissions that allow the traffic.

  Without a STUN server, peers often cannot even learn their public
  "server-reflexive" address; without a TURN relay, **symmetric NAT** on either
  side can make a direct path impossible. This app ships **no STUN/TURN** and
  therefore **does not promise Internet play**. When a direct connection cannot
  be formed, the app says so (the client shows "Establishing direct P2P
  connection…" and then a failure) rather than pretending success.

- **We do not add a hidden cloud relay.** If direct connectivity fails, it fails
  honestly. No Firebase, no hosted WebSocket, no matchmaking server, no TURN is
  quietly introduced.

## Why no dedicated signaling server?

Because the project's hard requirement is a **serverless** architecture: friends
should be able to play using only their phones. The host phone provides the
*temporary* signaling instead of a permanent backend. The trade-off is the
Internet-NAT limitation above, which we document rather than hide.

## STUN / TURN (Internet play)

The `iceServers: List<IceServer>` field flows from the host through
`JoinAccepted` into `PeerConnection.RTCConfiguration`, and each client also
merges its own device config on top (`ClientController.localIceServers`).

- **STUN is on by default** (public Google STUN — address discovery only, it
  relays no media). It is harmless on a LAN (local candidates win) and is the
  minimum needed for any chance of a direct Internet connection. Toggle it in
  **Settings**.
- **TURN is optional and user-supplied.** The app ships **no** TURN server (that
  would be dedicated infrastructure). Behind hostile/symmetric NAT a TURN relay
  is the only thing that guarantees connectivity — paste your own
  `turn:host:3478` + username/credential in **Settings → Internet connectivity**.

### Actually connecting two phones over the Internet — the honest checklist

Even with STUN/TURN, **the host must be reachable for the initial join**, because
the signaling server lives on the host phone. So Internet play needs, on the
**host** side, one of:

1. **Router port-forwarding**: forward TCP port `8080` (signaling) to the host
   phone's LAN IP, and give the joiners the host's **public** IP. Then also
   configure STUN (default) and ideally a **TURN** relay so the WebRTC media can
   traverse NAT. Without TURN, media may still fail on symmetric NAT even once
   signaling succeeds.
2. **A publicly reachable IPv6** address on the host (some mobile carriers give
   one). Joiners use `[ipv6]:8080`.
3. A VPN/overlay (e.g. Tailscale/WireGuard) that puts both phones on one virtual
   LAN — then it behaves exactly like same-Wi-Fi mode.

If none of these is set up, **Internet joins will fail at the join step** — the
app now says so explicitly rather than pretending to connect. This is the direct
consequence of the no-dedicated-server rule: there is no rendezvous point in the
cloud, so the host itself has to be reachable. LAN remains the primary supported
mode.

## Voice topology and scaling

Voice is a full **mesh**: for _n_ players there are _n·(n‑1)/2_ audio links, and
each phone encodes/sends its mic to _n‑1_ peers. This is fine for the supported
**2–4 players**. It scales poorly beyond that (CPU, bandwidth, battery), which is
why the first version caps players low. A future version could introduce an
**SFU** — but that would be a media server, breaking the serverless goal, so it
is deliberately **not** in v1.

## Disconnection & host migration

- **A client drops:** the host keeps its seat and marks it `DISCONNECTED`. The
  client auto-reconnects to the *same seat* (stored `playerId`+token
  re-authenticate the WebSocket; WebRTC is rebuilt; a full state snapshot
  resyncs it).
- **The host drops:** for v1 the game **ends** for everyone (a `SESSION_ENDED`
  message is broadcast). We do not pretend the game continues.
- **Host migration** (a client becoming the new host) is intentionally **not**
  implemented in v1 to keep the first release reliable. The design leaves room
  for it: a surviving peer could start its own signaling service and the others
  could re-join it, seeding the new host from the last authoritative snapshot
  they hold. That is future work, not a promise here.

## Security notes

- The signaling service exposes only the three endpoints above, validates
  `playerId`/`token`/`roomCode`, caps message size (64 KB), rejects malformed
  JSON, and never exposes files or executes received data.
- QR payloads are parsed strictly (`uno://join?host=…&port=…&room=…`) and
  validated field-by-field; nothing from a QR is ever executed.
- Cleartext HTTP is permitted **only** for the LAN signaling handshake (there is
  no CA on a phone-to-phone LAN); the actual game and voice run over encrypted
  WebRTC regardless.
