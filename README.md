# BucketFix
**(!)** For **1.8.8 Spigot** servers only!
<hr>

## About
**Most 1.8.8 players might have encountered this exact issue many times already.**
<br>
You are playing PvP with a lava bucket in the hand and as soon you rightclick and flick at the same time, your lava splits into two halfs. One half non existant and the another one flowing. 
<br>
<br>
**BucketFix solves this problem so that you do not have to go crazy about it any longer!**

## How does it work?
It sends a fake GameStateChange packet to the player that forces the client to think it's in adventure mode. Because it isn't possible to place/break blocks in adventure mode, the client won't display the client-sided lava block. Short after the gamemode changes back to survival so that the player is able to place/break blocks again.