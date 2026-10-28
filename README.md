# Lynk API

Stop arguing in the group chat. Just Lynk.

Friends create a hangout, invite their squad, vote on a spot everyone can reach, split the bill, and show up. This is
the backend: a Kotlin **Spring Boot modular monolith** running in production on Railway, with a Kotlin Multiplatform
client for Android and iOS.

**Live:** https://api.lynk.com.ng · **Client:** [Lynk (KMP)](https://github.com/EEseka/Lynk) ·
**Website:** [lynk.com.ng](https://lynk.com.ng)

---

## What it does

| Feature           | What it does                                                                                                                            |
|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| **Auth**          | Google Sign-In and guest accounts, JWT with rotating refresh tokens, plus an API key on every request                                   |
| **Hangouts**      | Create, edit, cancel, look a friend up by username and invite them, RSVP, attendee limits where a pending invite already holds the slot |
| **Spots**         | Search, trending and a Top 10 from Google Places, sorted into hangout categories, Redis-cached, saved spots                            |
| **Lynk AI**       | Claude Sonnet as a spot helper over Server-Sent Events, searching real places with tools, for Discover and for a hangout while it votes |
| **Live lobby**    | Raw WebSockets: presence, spot proposals, live vote tallies, host tie-break                                                             |
| **Payments**      | Paystack bill-splitting: per-share charges, deadlines, host payouts, automatic refunds                                                  |
| **Albums**        | A private photo album per finished hangout, uploaded straight to Supabase Storage through signed links                                  |
| **Notifications** | Firebase push, an in-app inbox, and transactional email through Brevo                                                                   |

## Built with

<table>
  <tr>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/kotlin/kotlin-original.svg" width="44" height="44" alt="Kotlin" /><br><b>Kotlin</b><br>2.4</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="44" height="44" alt="Spring Boot" /><br><b>Spring Boot</b><br>4.0</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="44" height="44" alt="PostgreSQL" /><br><b>PostgreSQL</b><br>17</td>
    <td align="center" width="110"><img src="https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://testcontainers.com&size=128" width="44" height="44" alt="Testcontainers" /><br><b>Testcontainers</b><br>real Postgres in tests</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/redis/redis-original.svg" width="44" height="44" alt="Redis" /><br><b>Redis</b><br>cache, limits</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/rabbitmq/rabbitmq-original.svg" width="44" height="44" alt="RabbitMQ" /><br><b>RabbitMQ</b><br>events</td>
  </tr>
  <tr>
    <td align="center" width="110"><img src="https://cdn.simpleicons.org/flyway" width="44" height="44" alt="Flyway" /><br><b>Flyway</b><br>migrations</td>
    <td align="center" width="110"><img src="https://cdn.simpleicons.org/claude" width="44" height="44" alt="Claude" /><br><b>Claude</b><br>Lynk AI</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/jetbrains/jetbrains-original.svg" width="44" height="44" alt="Koog" /><br><b>Koog</b><br>AI agent framework</td>
    <td align="center" width="110"><img src="https://cdn.simpleicons.org/googlemaps" width="44" height="44" alt="Google Places" /><br><b>Google Places</b><br>spots</td>
    <td align="center" width="110"><img src="https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://paystack.com&size=128" width="44" height="44" alt="Paystack" /><br><b>Paystack</b><br>payments</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/firebase/firebase-original.svg" width="44" height="44" alt="Firebase" /><br><b>Firebase</b><br>push</td>
  </tr>
  <tr>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/supabase/supabase-original.svg" width="44" height="44" alt="Supabase" /><br><b>Supabase</b><br>database, storage</td>
    <td align="center" width="110"><img src="https://cdn.simpleicons.org/brevo" width="44" height="44" alt="Brevo" /><br><b>Brevo</b><br>email</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/docker/docker-original.svg" width="44" height="44" alt="Docker" /><br><b>Docker</b><br>image</td>
    <td align="center" width="110"><img src="https://cdn.simpleicons.org/railway/7C8798" width="44" height="44" alt="Railway" /><br><b>Railway</b><br>hosting</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/githubactions/githubactions-original.svg" width="44" height="44" alt="GitHub Actions" /><br><b>GitHub Actions</b><br>CI</td>
    <td align="center" width="110"><img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/gradle/gradle-original.svg" width="44" height="44" alt="Gradle" /><br><b>Gradle</b><br>build</td>
  </tr>
</table>

## Architecture

### The system

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "-apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif", "fontSize": "15px", "primaryColor": "#C4EFC9", "primaryTextColor": "#1F2937", "primaryBorderColor": "#3E9A5B", "lineColor": "#7C8798", "clusterBkg": "#F6FBF6", "clusterBorder": "#3E9A5B", "titleColor": "#1F2937"}}}%%
flowchart TB
    CLIENT(["<b>Lynk app</b><br/>Android · iOS"])
    PSHOOK(["<b>Paystack</b><br/>webhooks"])
    GATE["<b>Security on every request</b><br/>API key · JWT · rate limits · guest guard"]

    subgraph SERVER ["Lynk API · Spring Boot"]
        direction LR
        USER["<b>user</b>"]
        HANGOUT["<b>hangout</b>"]
        LOBBY["<b>lobby</b>"]
        SPOT["<b>spot</b>"]
        AI["<b>assistant</b>"]
        PAYMENT["<b>payment</b>"]
        NOTIF["<b>notification</b>"]
    end

    GSIGN["Google<br/>Sign-In"]
    STORAGE["Supabase<br/>Storage"]
    subgraph DATA ["Data"]
        direction LR
        PG[("<b>PostgreSQL</b><br/>a schema per module")]
        REDIS[("<b>Redis</b><br/>caches, rate limits")]
    end
    PLACES["Google<br/>Places"]
    CLAUDE["Anthropic<br/>Claude"]
    PAYSTACK["Paystack"]
    FCM["Firebase<br/>Cloud Messaging"]
    BREVO["Brevo"]

    VIA("REST · SSE · WebSocket")
    SIGNED("signature checked")
    CLIENT --> VIA --> GATE
    GATE ==> SERVER
    PSHOOK --> SIGNED ==> SERVER

    USER --> GSIGN
    USER --> STORAGE
    HANGOUT --> STORAGE
    SERVER ==> DATA
    SPOT --> PLACES
    AI --> CLAUDE
    PAYMENT --> PAYSTACK
    NOTIF --> FCM
    NOTIF --> BREVO

    classDef client fill:#FFE2BF,stroke:#D9822B,color:#1F2937,stroke-width:2px
    classDef gate fill:#F8C8CB,stroke:#B85560,color:#1F2937,stroke-width:2px
    classDef unit fill:#C4EFC9,stroke:#3E9A5B,color:#1F2937,stroke-width:2px
    classDef store fill:#DCCEF7,stroke:#7C5BC4,color:#1F2937,stroke-width:2px
    classDef ext fill:#DCEBFA,stroke:#3F86C2,color:#1F2937,stroke-width:2px
    classDef event fill:#FFF4D6,stroke:#D9A62B,color:#1F2937,stroke-width:1px
    class CLIENT,PSHOOK client
    class GATE gate
    class USER,SPOT,HANGOUT,LOBBY,PAYMENT,NOTIF,AI unit
    class PG,REDIS store
    class GSIGN,STORAGE,PLACES,CLAUDE,PAYSTACK,FCM,BREVO ext
    class VIA,SIGNED event
    style DATA fill:#FAF7FF,stroke:#7C5BC4,color:#1F2937
```

Every request from the app passes an API key filter, then a JWT filter, then per-IP and per-user rate limits in Redis,
then a guard that refuses any write from a guest account. Paystack's webhooks go straight to `payment`, skip the
first two, and are accepted only with a valid HMAC signature.

### The modules

`app` is the only Spring Boot application. Every other Gradle module is a library that owns one slice of the domain
and its own Postgres schema. Other modules call its services and use its domain types, but never touch its
repositories or tables. The one shared piece of API is `spot`'s `SpotDto`, so a place looks the same in every
response that shows one.

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "-apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif", "fontSize": "15px", "primaryColor": "#C4EFC9", "primaryTextColor": "#1F2937", "primaryBorderColor": "#3E9A5B", "lineColor": "#7C8798", "clusterBkg": "#F6FBF6", "clusterBorder": "#3E9A5B", "titleColor": "#1F2937"}}}%%
flowchart TB
    APP["<b>app</b><br/>Spring Boot entry point<br/>security, jobs, migrations"]

    subgraph MODULES ["Feature modules · arrows are service calls"]
        direction LR
        AI["<b>assistant</b><br/>Lynk AI"]
        LOBBY["<b>lobby</b><br/>live WebSocket, voting"]
        PAYMENT["<b>payment</b><br/>charges, payouts, refunds"]
        HANGOUT["<b>hangout</b><br/>hangouts, invites, albums"]
        SPOT["<b>spot</b><br/>Google Places, saved spots"]
        USER["<b>user</b><br/>auth, profiles, API keys"]
        NOTIF["<b>notification</b><br/>push, inbox, email"]

        AI --> LOBBY
        AI --> HANGOUT
        AI --> SPOT
        LOBBY --> HANGOUT
        LOBBY --> SPOT
        PAYMENT --> HANGOUT
        HANGOUT --> SPOT
    end

    COMMON["<b>common</b><br/>shared types, events,<br/>JWT, messaging"]

    APP ==> MODULES
    MODULES ==> COMMON

    classDef app fill:#F8C8CB,stroke:#B85560,color:#1F2937,stroke-width:2px
    classDef common fill:#B9DCF7,stroke:#3F86C2,color:#1F2937,stroke-width:2px
    classDef unit fill:#C4EFC9,stroke:#3E9A5B,color:#1F2937,stroke-width:2px
    class APP app
    class COMMON common
    class USER,NOTIF,AI,PAYMENT,LOBBY,HANGOUT,SPOT unit
```

Inside every module the same four layers, and dependencies only point one way:

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "-apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif", "fontSize": "15px", "primaryColor": "#FDEBA6", "primaryTextColor": "#1F2937", "primaryBorderColor": "#B8901A", "lineColor": "#7C8798"}}}%%
flowchart LR
    API["<b>api</b><br/>controllers, DTOs,<br/>WebSocket, SSE"]
    SERVICE["<b>service</b><br/>business rules,<br/>transactions, jobs"]
    INFRA["<b>infra</b><br/>JPA, Redis, RabbitMQ,<br/>HTTP clients"]
    DOMAIN["<b>domain</b><br/>models, events,<br/>exceptions"]

    API --> SERVICE
    SERVICE --> INFRA
    API --> DOMAIN
    SERVICE --> DOMAIN
    INFRA --> DOMAIN

    classDef layer fill:#FDEBA6,stroke:#B8901A,color:#1F2937,stroke-width:2px
    class API,SERVICE,INFRA,DOMAIN layer
```

```
app            Spring Boot entry point, security, rate limiting, scheduling, Flyway migrations
common         shared types, events, JWT, exception handling, RabbitMQ and storage plumbing
user           auth, profiles, API keys, profile photos
hangout        hangouts, participants, invites, RSVP, albums, the payment state machine
spot           Google Places integration, trending and Top 10 cache, saved spots
lobby          raw WebSocket lobby, presence, voting
payment        Paystack charges, webhooks, payouts, refunds, reconciliation
notification   FCM push, inbox rows, Thymeleaf email
assistant      Lynk AI: the Claude tool loop, chats, daily limits, SSE streaming
```

### Events between modules

Modules never read each other's tables. `hangout` learns a person exists because `user` published an event, and
keeps its own copy of what it needs. Events cross modules over RabbitMQ: `user` announces sign-ups, profile changes
and deletions; `hangout` announces invites, RSVPs, reminders, payments, photos and cancellations. Deleting an account
also cleans up everywhere: saved spots, chats, album photos, the inbox and device tokens. The live lobby hears about
changes inside the same process, once the database transaction has committed.

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "-apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif", "fontSize": "15px", "primaryColor": "#C4EFC9", "primaryTextColor": "#1F2937", "primaryBorderColor": "#3E9A5B", "lineColor": "#7C8798"}}}%%
flowchart LR
    USER_P["<b>user</b>"]
    HANGOUT_P["<b>hangout</b>"]
    PAYMENT_P["<b>payment</b>"]
    SPOT_P["<b>spot</b>"]

    E1("user events")
    E2("hangout events")
    E3("refund issued")
    E4("place details")

    MQ{{"<b>RabbitMQ</b>"}}

    L1("all of them")
    L2("profile changes")
    L3("user deleted")
    L4("cancelled, left")
    L5("deletions, place details")

    NOTIF_C["<b>notification</b><br/>keeps its own copy of each person,<br/>sends push, inbox row, email"]
    HANGOUT_C["<b>hangout</b><br/>keeps its own copy of each person"]
    SPOT_C["<b>spot</b><br/>drops a deleted user's saved spots"]
    PAYMENT_C["<b>payment</b><br/>refunds whoever is owed"]
    AI_C["<b>assistant</b><br/>refreshes cards, deletes old chats"]

    LIVE("in process, after commit")
    LOBBY_C["<b>lobby</b><br/>tells everyone in the hangout<br/>over the WebSocket"]

    USER_P --> E1 --> MQ
    HANGOUT_P --> E2 --> MQ
    PAYMENT_P --> E3 --> MQ
    SPOT_P --> E4 --> MQ

    MQ --> L1 --> NOTIF_C
    MQ --> L2 --> HANGOUT_C
    MQ --> L3 --> SPOT_C
    MQ --> L4 --> PAYMENT_C
    MQ --> L5 --> AI_C

    HANGOUT_P -.-> LIVE -.-> LOBBY_C

    classDef unit fill:#C4EFC9,stroke:#3E9A5B,color:#1F2937,stroke-width:2px
    classDef mq fill:#FFD9B3,stroke:#E0752D,color:#1F2937,stroke-width:2px
    classDef event fill:#FFF4D6,stroke:#D9A62B,color:#1F2937,stroke-width:1px
    classDef live fill:#EDE7FB,stroke:#7C5BC4,color:#1F2937,stroke-width:1px
    class USER_P,HANGOUT_P,PAYMENT_P,SPOT_P,NOTIF_C,HANGOUT_C,SPOT_C,PAYMENT_C,AI_C,LOBBY_C unit
    class MQ mq
    class E1,E2,E3,E4,L1,L2,L3,L4,L5 event
    class LIVE live
```

### The payment state machine

Every change of payment state comes from a signature-checked Paystack webhook, a host action, or a scheduled sweep
that reads the current state from the database. None comes from a message on the queue, since a queued message can
arrive twice, late, or out of order.

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "-apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif", "fontSize": "15px", "primaryColor": "#C4EFC9", "primaryTextColor": "#1F2937", "primaryBorderColor": "#3E9A5B", "lineColor": "#7C8798"}}}%%
flowchart TB
    START(("start"))
    COLLECTING["COLLECTING"]
    AWAIT["AWAITING HOST<br/>DECISION"]
    READY["READY FOR<br/>PAYOUT"]
    PAYING["PAYING OUT"]
    PAID["PAID OUT"]
    FAILED["PAYOUT FAILED"]
    CANCELLED["CANCELLED"]

    T0("host turns payments on")
    T1("deadline passes,<br/>everyone paid")
    T2("deadline passes,<br/>someone has not paid")
    T3("host extends<br/>the deadline")
    T4("host drops non-payers, last<br/>share paid, or start time arrives")
    T5("payout sweep<br/>sends the transfer")
    T6("nothing was<br/>collected")
    T7("Paystack confirms<br/>the transfer")
    T8("refused, failed<br/>or never sent")
    T9("host retries")
    TC("hangout cancelled,<br/>everyone refunded")

    START --> T0 --> COLLECTING
    COLLECTING --> T1 --> READY
    COLLECTING --> T2 --> AWAIT
    AWAIT --> T3 --> COLLECTING
    AWAIT --> T4 --> READY
    READY --> T5 --> PAYING
    READY --> T6 --> PAID
    PAYING --> T7 --> PAID
    PAYING --> T8 --> FAILED
    FAILED --> T9 --> READY

    COLLECTING -.-> TC
    AWAIT -.-> TC
    READY -.-> TC
    FAILED -.-> TC
    TC -.-> CANCELLED

    classDef money fill:#C4EFC9,stroke:#3E9A5B,color:#1F2937,stroke-width:2px
    classDef waiting fill:#FFE2BF,stroke:#D9822B,color:#1F2937,stroke-width:2px
    classDef bad fill:#F8C8CB,stroke:#B85560,color:#1F2937,stroke-width:2px
    classDef event fill:#FFF4D6,stroke:#D9A62B,color:#1F2937,stroke-width:1px
    classDef start fill:#7C8798,stroke:#7C8798,color:#FFFFFF
    class COLLECTING,READY,PAYING,PAID money
    class AWAIT waiting
    class FAILED,CANCELLED bad
    class T0,T1,T2,T3,T4,T5,T6,T7,T8,T9,TC event
    class START start
```

### How a payment actually lands

The client never tells the server that it paid. Paystack does, over a signed callback.

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "-apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif", "fontSize": "15px", "actorBkg": "#C4EFC9", "actorBorder": "#3E9A5B", "actorTextColor": "#1F2937", "actorLineColor": "#7C8798", "signalColor": "#7C8798", "signalTextColor": "#7C8798", "noteBkgColor": "#FFF4D6", "noteBorderColor": "#D9A62B", "noteTextColor": "#1F2937", "activationBkgColor": "#DCEBFA", "activationBorderColor": "#3F86C2", "sequenceNumberColor": "#1F2937"}}}%%
sequenceDiagram
    participant C as Client
    participant API as Lynk API
    participant P as Paystack
    participant DB as PostgreSQL
    C ->> API: POST /api/payments/hangouts/{id}/initialize
    API ->> DB: save PENDING payment (committed first)
    API ->> P: initialize charge
    P -->> API: checkout url
    API -->> C: url
    C ->> P: pays on Paystack's page
    P ->> API: POST /api/payments/webhook (HMAC signed)
    API ->> API: verify signature, else reject
    API ->> DB: lock this person's payments for this hangout
    API ->> DB: mark SUCCESS, participant has paid
    Note over DB: partial unique index makes a<br/>second SUCCESS row impossible
    API -->> P: 200 OK, so Paystack stops resending it
    Note over API: a sweep reconciles anything<br/>the webhook never delivered
```

The `PENDING` row is committed *before* Paystack is called. If those shared a transaction, and it rolled back, Paystack
would hold a live charge against a reference this database had never heard of, and the money would arrive with nothing
to attach it to.

### Things worth a closer look

**A payment lifecycle that survives failure.** Charges are confirmed by a signature-verified Paystack webhook, never by
the client. Scheduled sweeps resolve deadlines, chase stuck payments, retry failed refunds, and reconcile payouts
against the provider. Every refund and every payout attempt is its own row, so the history of where the money went is
never overwritten. A pessimistic lock on the payer's rows, plus a partial unique index on `WHERE status = 'SUCCESS'`,
means a second payment for the same share is caught and sent back, even when two webhooks land at once.

**Concurrency handled on purpose.** The hangout row has many writers (the host, attendees, webhooks, sweeps), so it
carries an optimistic `@Version`, and every change to who is in a hangout first takes a row lock, so two people
accepting the last free share can't both get in.

**Lynk AI on real data.** The assistant is a tool loop over Claude: it searches Google Places through the `spot`
module, reads opening hours and amenities, and shows places as cards the app can open, save, or put on the ballot. It
can only recommend places its own tools returned, answers stream over Server-Sent Events, and each answer's token
usage is stored.

**Schema owned by migrations.** Flyway builds every environment from `V1__baseline.sql`; Hibernate runs `validate`
everywhere and creates nothing, including in development.

**Defence in depth.** Default-deny security, an API key filter ahead of the chain, guest accounts that cannot write,
per-IP and per-user rate limiting in Redis, and account-deletion guards that refuse to strand money mid-flight.

## Testing

The integration tests run against **real PostgreSQL in Testcontainers**, built by the same migrations production
uses. Outside services (Paystack, Google, Claude, Firebase, Brevo, Supabase Storage) are replaced with mocks, and
RabbitMQ with an in-memory broker that hands each event to the real listeners.

```
Unit                  money arithmetic, webhook signatures, opening hours, place types, Top 10 ranking
REST journeys         auth, hangouts, participants, spots, profile, albums, notifications, payments
Scheduled sweeps      every one, invoked directly, since no endpoint can reach them
Payment webhook       a signed Paystack callback driving the full state machine
Concurrency           eight people racing for the last free share
Lynk AI               a scripted stand-in for Claude driving the whole tool loop and answer stream
Email                 every template branch rendered
```

```bash
./gradlew build        # compiles and runs everything; needs Docker running
```

## Running it

Needs **JDK 21** and **Docker**.

```bash
git clone https://github.com/EEseka/lynk-api.git && cd lynk-api
cp .env.example .env       # then fill it in
./gradlew build
set -a && source .env && set +a      # load .env into this shell, Spring Boot doesn't read it on its own
SPRING_PROFILES_ACTIVE=dev ./gradlew :app:bootRun
```

API docs are served in development only, at `http://localhost:8080/swagger-ui/index.html`. They are switched off in
production, so the API surface isn't published.

### Configuration

`application.yaml` holds everything shared, `application-dev.yaml` and `application-prod.yaml` hold what differs, and
every secret arrives from the environment. Nothing sensitive is in the repository. For push notifications locally,
you can skip `FIREBASE_CREDENTIALS_JSON` and put the service account file at
`app/src/main/resources/firebase-credentials/lynk-firebase-adminsdk.json` instead, which git ignores.

| Variables                                              | Used for                                         |
|--------------------------------------------------------|--------------------------------------------------|
| `POSTGRES_PASSWORD` `DATABASE_URL` `DATABASE_USERNAME` | Postgres                                         |
| `SUPABASE_SERVICE_KEY`                                 | profile photo and album storage                  |
| `REDIS_PASSWORD` `RABBITMQ_PASSWORD`                   | cache and message queue                          |
| `JWT_SECRET_BASE64` `ADMIN_USERNAME` `ADMIN_PASSWORD`  | auth                                             |
| `GOOGLE_CLIENT_ID` `GOOGLE_PLACES_KEY`                 | Google Sign-In and Places                        |
| `PAYSTACK_SECRET_KEY`                                  | payments                                         |
| `BREVO_API_KEY`                                        | email, over Brevo's HTTP API                     |
| `ANTHROPIC_API_KEY`                                    | Lynk AI                                          |
| `ASSISTANT_DISABLED`                                   | optional; `true` switches Lynk AI off            |
| `FIREBASE_CREDENTIALS_JSON`                            | push notifications, the service account JSON     |

## Deployment

```mermaid
%%{init: {"theme": "base", "themeVariables": {"fontFamily": "-apple-system, BlinkMacSystemFont, Segoe UI, Helvetica, Arial, sans-serif", "fontSize": "15px", "primaryColor": "#B9DCF7", "primaryTextColor": "#1F2937", "primaryBorderColor": "#3F86C2", "lineColor": "#7C8798"}}}%%
flowchart LR
    PUSH["push to main"] --> CI["<b>GitHub Actions</b><br/>every test, real Postgres<br/>in Testcontainers"]
    CI --> GREEN("green") --> BUILD["<b>Railway</b><br/>multi-stage Dockerfile"]
    CI --> RED("red") --> STOP["nothing deploys"]
    BUILD --> IMG["<b>image</b><br/>JRE + one jar,<br/>non-root, no source"]
    IMG --> HEALTH{"/actuator/health<br/>/readiness"}
    HEALTH --> UP("up") --> LIVE["<b>api.lynk.com.ng</b>"]
    HEALTH --> DOWN("never healthy") --> ROLL["previous container stays"]

    classDef step fill:#B9DCF7,stroke:#3F86C2,color:#1F2937,stroke-width:2px
    classDef good fill:#C4EFC9,stroke:#3E9A5B,color:#1F2937,stroke-width:2px
    classDef bad fill:#F8C8CB,stroke:#B85560,color:#1F2937,stroke-width:2px
    classDef event fill:#FFF4D6,stroke:#D9A62B,color:#1F2937,stroke-width:1px
    class PUSH,CI,BUILD,IMG,HEALTH step
    class LIVE good
    class STOP,ROLL bad
    class GREEN,RED,UP,DOWN event
```

Push to `main` → GitHub Actions runs every test → Railway builds the multi-stage Dockerfile and deploys only if they
passed. The runtime image carries a JRE and one jar: no compiler, no source, no credentials, and it runs as an
unprivileged user.

Railway moves traffic to a new deployment only once `/actuator/health/readiness` answers, rather than the full health
endpoint, so a Redis or RabbitMQ blip can't hold up a deployment that is otherwise ready.

## Status

Backend feature-complete and deployed. The [KMP client](https://github.com/EEseka/Lynk) is out on Android: download the
APK from [lynk.com.ng](https://lynk.com.ng). The App Store and Google Play releases come next.

Payments are built and tested end to end against Paystack's test mode, but they do not take real money. Holding
attendees' money until the payment deadline counts as a regulated activity in Nigeria, and the licence it needs from
the Central Bank is out of reach for a solo developer.

## License

All rights reserved. The code is public to read, but not licensed for use. See [LICENSE](LICENSE).
