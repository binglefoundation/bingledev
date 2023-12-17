## Unit testing
- ~~remove RelayApp and make handler~~
- ~~pull relay code from Worker~~
- ~~add relay unit test~~
- ~~remove DistributedDBApp and make handler~~
- ~~remove apps stuff~~

- ~~make a resolver that uses a DDB endpoint (relay)~~
- ~~unit test resolver at engine level~~
- ~~unit test resolver directly~~
- ~~make an advertiser that uses a DDB endpoint (relay)~~
- ~~unit test advertiser~~

- ~~make RelayFinder follow the root then epoch based lookup~~
- ~~unit test RelayFinder~~

- ~~unit test advertise at engine level
  (unstubbed resolver/advertiser)~~

- ~~initialize DDB as a relay node~~
- ~~unit test DDBInitialize for boot and join~~
- ~~test DDB handlers in engine~~
- ~~make jstun dependency own lib (revert to released version)~~
- ~~remove ISendableMessage~~
- ~~make stunResolver own module (StunProcessor)~~
- ~~unit test StunProcessor~~

## Simulator testing

- write sim test for 2 nodes, root relays, single message
- implement engine wrapper
- implement stun/address binding mocks
- implement operation scheduling
- implement config of engines
- implement message deliver measurement
- write test with 4 nodes, root relays, becoming second relay
- write test with 4 nodes, becoming 2/3 relay
- write extended tests with multiple nodes
- test unreliability

## Network testing

- port Algorand / Blockchain in
- test Algorand with Docker localnet
- port DTLS in
- test DTLS in local network
- test end to end in local network

## Testnet testing

- test Algorand on a testnet

## Deployment

- deploy root relays
- collect stats from root relays
- implement continuous tester
- implement reward/remove mech
