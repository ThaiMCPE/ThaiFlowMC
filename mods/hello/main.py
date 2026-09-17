from thaiflow import *


@load
def loaded():
    print("My mod loaded!")


@event("test")
def test_event(data):
    print(data)


@server_start
def ready(server):
    server.broadcast("hello mod is ready!")


@server_stop
def bye(server):
    print("hello mod saw the server stopping!")
