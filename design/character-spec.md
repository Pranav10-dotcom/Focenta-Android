# ScrollBloom Character — Production Spec

## Personality
Cute, expressive, supportive, never judgmental.

## Layer structure
- Body
- Left leaf
- Right leaf
- Top leaves
- Flower petals
- Flower center
- Left eye
- Right eye
- Eyebrows
- Mouth
- Left arm
- Right arm
- Left foot
- Right foot
- Optional particles

## State machine inputs
- health: 0–100
- reels: integer
- mood: happy / tired / thirsty / exhausted / sleeping
- reelSeen: trigger
- breakStarted: trigger
- milestone: integer
- recoveryComplete: trigger

## Animations
Idle:
- blink
- sway
- look around
- stretch
- yawn
- smell flower
- wave
- sleep

Reel detected:
- notice
- eyes widen
- small bounce
- count update
- leaf/petal reaction
- settle

Recovery:
- cloud appears
- rain
- drinking
- new leaf
- flower opens
- celebration

## Health states
0–5: very happy
6–10: happy
11–20: getting tired
21–30: thirsty
31–45: exhausted
46–60: very exhausted
60+: sleeping / drained

## Design rule
Do not shame the user. The plant is a companion and visual feedback mechanism, not a punishment.
