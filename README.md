# DonutCommands 2.8.9 — Source

Java/Maven source project. No prebuilt JAR is included.

## Build
- JDK 21
- Maven 3.9+
- Run `mvn clean package`
- Expected output: `target/DonutCommands-2.8.9.jar`

## Target software
- Paper
- Purpur
- Spigot
- Folia (plugin metadata declares `folia-supported: true`)

The project compiles against Paper API 1.20.1 and targets Java 21. Paper and Purpur are the primary targets; Spigot compatibility should be verified on the exact server build. Folia support is enabled in plugin metadata and command dispatch/shared YAML command mutations are serialized, but this source has not been runtime-certified on Folia. In particular, cross-region entity messaging and inventory-close behavior must be tested on the exact Folia build before production use. Do not treat the metadata flag alone as proof of complete Folia safety.

A single build does not guarantee compatibility with every Minecraft version from 1.20.1 through 26.2 or every Java version from 21 through 27. Newer server versions may require updating the API dependency and recompiling.

## Existing commands
`/bal`, `/pay`, `/baltop`, `/shards`, `/shardpay`, `/sell`, `/sellhand`, `/sellall`, `/sellhistory`, `/worth`, `/prices`, `/price reload`, `/price set`, `/ah` (`sell/search/my/buy/cancel`), `/orders` (`create/fill/my/cancel`), `/order`, `/bounty` (`add` and lookup/board), `/leaderboard`, `/stats`, `/ping`, `/findplayer`, `/block`, `/ignore`, `/unblock`, `/friend`, `/settings`, `/help`, `/rules`, `/discord`, `/ranks`, `/meta`, `/whereami`, `/link`, `/api`, `/donutcore`, `/dcadmin`.

## Data files
- `price.yml`: materials available on the running server are added automatically at first load. Edit prices with numbers or suffixes `k/m/b/t`.
- `balances.yml`, `shards.yml`, `market.yml`, `orders.yml`, `bounties.yml`, `social.yml`, `history.yml`, `profiles.yml`: created automatically.

## Important behavior notes
This is an independent DonutSMP-inspired plugin, not official DonutSMP code or an exact clone. Some network-specific features are local/basic equivalents. Auction and order workflows are command-based rather than full DonutSMP GUI systems. `/leaderboard`, `/link`, and `/api` are limited local/basic implementations. `/medal` and `/shop` are not implemented in this 2.8.9 source. Included `price.yml` values are examples, not verified current DonutSMP prices. Test economy and market behavior on a staging server and back up data before upgrading.
