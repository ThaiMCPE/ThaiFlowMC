from thaiflow import *

item("ruby")


@join
def hello(player):
    player.say("Hi!")
