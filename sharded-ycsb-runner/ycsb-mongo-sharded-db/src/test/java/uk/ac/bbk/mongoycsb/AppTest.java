package uk.ac.bbk.mongoycsb;

import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest {
    /**
     * Rigorous Test :-)
     */
    @Test
    public void shouldAnswerWithTrue() {
        ShardedMongo sma = new ShardedMongo();
        assertTrue(sma instanceof ShardedMongo);

        assertTrue(true);
    }
}
