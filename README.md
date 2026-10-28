# Quelea

Quelea is the distributed party system for the OneLiteFeather network. Players form a party, and when the party leader joins a minigame from the lobby, the rest of the party follows. The system is a Micronaut HTTP service (`backend`), a Velocity proxy plugin (`velocity`) and a Java client (`client`) generated from the backend's OpenAPI document. Planning and specifications live in `openspec/`.

## Development

```sh
./gradlew build
```
