rs.initiate({
    _id: "configServerRS",
    configsvr: true,
    members: [
        { _id: 0, host: "config-server-svc-1" },
        { _id: 1, host: "config-server-svc-2" },
        { _id: 2, host: "config-server-svc-3" }
    ]
});