# DiamondBanking
Spigot plugin that provides the ability to turn diamonds into currency, and vice versa.

## Configuration
```YAML
AllowLittering: true
DiamondWorth: 300
```

if **AllowLittering** is true, the diamonds on a withdrawal will be placed at the players feet if their inventory is full.

if **AllowLittering** is false, on a withdrawal where the players inventory is full, the diamonds will attempt to go into their EnderChest.

Anything not successfully withdrawn will be refunded.

**DiamondWorth** is the amount of currency a diamond is worth. This will dictate the buying and selling.

## Commands

/withdraw [amount]
- Will give you the requested amount of diamonds.

/deposit
- Will deposit any diamonds that are in the players main hand.
