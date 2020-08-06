rs.initiate({
    _id: "configServerRS",
    configsvr: true,
    members: [
        { _id: 0, host: "config-server-svc-1" },
        { _id: 1, host: "config-server-svc-2" },
        { _id: 2, host: "config-server-svc-3" }
    ]
});

/**
 * Converts cartesian coordiantes to polar ones between -2 and 2
 * 
 * @param {number} x X-Coordinate
 * @param {number} y Y-Coordinate
 * 
 * @returns {Object} returned object comprises of re and im representing real and imaginary parts.
 */
db.system.js.save({
    _id: 'cartToPol',
    value: function (x, y) {
        return {
            re: -2 + ((x / WIDTH) * 4),
            im: -2 + ((y / HEIGHT) * 4)
        }
    }
});

/**
 * 
 * @param {Object} z 
 * @param {Object} c 
 * @param {number} iterations 
 */
db.system.js.save({
    _id: 'mandelbrot',
    value: function (z, c, iterations) {
        if (iterations == 0) {
            return -1;
        }

        var M = {
            re: ((z.re * z.re) - (z.im * z.im)) + c.re,
            im: (2 * (z.im * z.re)) + c.im
        }

        var M_abs = Math.sqrt((M.re * M.re) + (M.im * M.im));

        if (M_abs > 4.0) {
            return iterations;
        }

        return mandelbrot(z, c, iterations - 1);
    }
});