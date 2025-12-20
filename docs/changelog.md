# Changelog

* Unreleased:
  * FTC: Add `FtcLoggingSession` composition helper for `LinearOpMode` logging
  * FTC: Pinpoint odometry logging defaults to on (no-op when no device is present), with opt-out
  * FTC: Add `PedroFollowerPoseLogger` (reflection-based; no Pedro dependency)
  * FTC: DriverStation joystick logging compatible with AdvantageScope Joysticks schema

* 0.0.1: Initial release; most AdvantageScope panels supported, namely:
  * Graph
  * 2D field
  * 3D field
  * Table 
  * Stats
  * Mechanism
  * Points
  
* 0.0.2: Add `System.out` logging

* 0.0.3: First public release for beta testing

* 0.1.0-beta2:
  * FTC: HardwareMap wrapper improvements for replay determinism (records a device manifest under `HardwareMap/_manifest`, supports subclass lookups like `DcMotorEx`)
  * Core: RLOG decoding fixes (array decoding + float support) and safer EOF handling for file replay
  * Tools: Adds a small desktop CLI module (`:tools`) for dumping replayed signals from `.rlog` files
