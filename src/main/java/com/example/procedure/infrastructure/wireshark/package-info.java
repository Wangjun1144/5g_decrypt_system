/**
 * Wireshark-specific infrastructure support.
 *
 * This package owns local toolchain configuration, profile preparation and
 * startup-time verification for tshark/text2pcap. Concrete decode and build
 * adapters continue to live under {@code infrastructure.decode}, while this
 * package keeps the local Wireshark runtime contract stable and observable.
 */
package com.example.procedure.infrastructure.wireshark;
