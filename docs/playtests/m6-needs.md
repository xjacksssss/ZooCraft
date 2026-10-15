# M6 play-test: needs, spending and toilets

Set up a zoo with a café (a counter and a barrel of bread and honey bottles), a souvenir stall (a chest of flowers) and two toilets. `/zoo debug needs <hunger> <toilet>` sets every visitor's needs so you don't have to wait, and `/zoo debug visitors` lists each visitor's budget, hunger, toilet need and spending.

## Food and drink
- [ ] Set needs to `75 0`. Hungry visitors walk to the café counter, buy one item each, and carry it in their arms while eating (sounds and crumbs). "That was delicious!" appears, and their hunger drops (bread by 60, honey by 35).
- [ ] The Shops tab shows the stock falling and "Sold" rising; Overview "Shop income" and the treasury rise by the prices.
- [ ] Nobody buys a second item while still eating the first.
- [ ] Raise bread to 9 emeralds: visitors say "That's far too expensive." and the tab shows missed sales. Lower it again and they buy.
- [ ] Empty the barrel: visitors who were on their way say "They've sold out!", try another shop if there is one, then carry on looking at animals. Restock it and they buy again.
- [ ] A visitor with almost no emeralds left says "I can't afford anything here." and heads home.
- [ ] Remove the café: hungry visitors say "There's nowhere to get something to eat." once each. If they stay starving for a minute, they leave.

## Souvenirs
- [ ] Visitors who decide to go home sometimes stop at the stall first, buy a flower, say "I'll take this home as a souvenir!", carry it for a while, and then leave.

## Toilets
- [ ] Set needs to `0 75`. Visitors go to the nearest toilet, stand there for a few seconds, and their toilet need returns to 0.
- [ ] With many visitors and one toilet, some wait nearby; after 20 seconds they try the other toilet, or say "The queue for the toilet is too long."
- [ ] Wall a toilet in completely: after a while visitors say "We couldn't get to the toilets." and stop trying it.
- [ ] Remove all toilets: visitors say "I couldn't find a toilet." and eventually leave if the need goes unmet.

## Safety
- [ ] Close the zoo while visitors are walking to the café: they turn round and leave, and nothing is charged.
- [ ] Save and quit while visitors are eating or queueing, then reload: no purchase is charged twice and nobody is stuck.
