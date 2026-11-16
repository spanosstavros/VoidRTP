VoidRTPCountdown
================
BUILD (needs Java 21 + Maven):
  mvn package
  -> target/VoidRTPCountdown-1.0.0.jar  -> put in /plugins

BETTERRTP (plugins/BetterRTP/config.yml):
  Delay:
    Enabled: false      # our plugin does the countdown now
  then /rtp reload

DELUXEMENUS (your head item):
  left_click_commands:
    - '[sound] BLOCK_LEVER_CLICK'
    - '[close]'
    - '[player] voidrtp world'

RESOURCE PACK: use Voidlandpack85_rtp.zip (has the bar glyphs).
