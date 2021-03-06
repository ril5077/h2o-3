package water.parser.orc;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import water.Job;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.api.schemas3.ParseSetupV3;
import water.fvec.Frame;
import water.fvec.NFSFileVec;
import water.fvec.Vec;
import water.parser.ParseDataset;
import water.parser.ParseSetup;
import water.parser.ParseWriter;
import water.util.ArrayUtils;
import water.util.Log;

import static org.junit.Assert.assertEquals;
import static water.parser.orc.OrcTestUtils.compareOrcAndH2OFrame;
import static water.util.FileUtils.*;

/**
 * Test suite for orc parser.
 *
 * This test will build a H2O frame for all orc files found in smalldata/parser/orc directory
 * and compare the H2O frame content with the orc file content read with Core Java commands.
 * Test is declared a success if the content of H2O frame is the same as the contents read
 * by using core Java commands off the Orc file itself.  No multi-threading is used in reading
 * off the Orc file using core Java commands.
 */
public class ParseTestOrc extends TestUtil {

    private int totalFilesTested = 0;
    private int numberWrong = 0;

    // list all orc files in smalldata/parser/orc directory
    private String[] allOrcFiles = {
            "smalldata/parser/orc/TestOrcFile.columnProjection.orc",
            "smalldata/parser/orc/bigint_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.emptyFile.orc",
            "smalldata/parser/orc/bool_single_col.orc",
//      "smalldata/parser/orc/TestOrcFile.metaData.orc", // do not support metadata from user
//      "smalldata/parser/orc/decimal.orc",
//      "smalldata/parser/orc/TestOrcFile.test1.orc",    // do not support metadata from user
            "smalldata/parser/orc/demo-11-zlib.orc",
            "smalldata/parser/orc/TestOrcFile.testDate1900.orc",
            "smalldata/parser/orc/demo-12-zlib.orc",
            "smalldata/parser/orc/TestOrcFile.testDate2038.orc",
            "smalldata/parser/orc/double_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.testMemoryManagementV11.orc",
            "smalldata/parser/orc/float_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.testMemoryManagementV12.orc",
            "smalldata/parser/orc/int_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.testPredicatePushdown.orc",
            "smalldata/parser/orc/nulls-at-end-snappy.orc",
//      "smalldata/parser/orc/TestOrcFile.testSeek.orc", // do not support metadata from user
//      "smalldata/parser/orc/orc-file-11-format.orc",   // different column names are used between stripes
            "smalldata/parser/orc/TestOrcFile.testSnappy.orc",
            "smalldata/parser/orc/orc_split_elim.orc",
            "smalldata/parser/orc/TestOrcFile.testStringAndBinaryStatistics.orc",
//          "smalldata/parser/orc/over1k_bloom.orc", // do not support metadata from user
            "smalldata/parser/orc/TestOrcFile.testStripeLevelStats.orc",
            "smalldata/parser/orc/smallint_single_col.orc",
//          "smalldata/parser/orc/TestOrcFile.testTimestamp.orc", // abnormal orc file, no inpsector structure available
            "smalldata/parser/orc/string_single_col.orc",
//          "smalldata/parser/orc/TestOrcFile.testUnionAndTimestamp.orc", // do not support metadata from user
            "smalldata/parser/orc/tinyint_single_col.orc",
            "smalldata/parser/orc/TestOrcFile.testWithoutIndex.orc",
//          "smalldata/parser/orc/version1999.orc" // contain only orc header, no column and no row, total file size is 0.
    };

    @BeforeClass
    static public void setup() { TestUtil.stall_till_cloudsize(1); }

    @Test 
    public void testTypeOverrides(){
        Scope.enter();
        try {
            NFSFileVec nfs = makeNfsFileVec("smalldata/parser/orc/orc_split_elim.orc");
            ParseSetup pstp = new ParseSetup(new ParseSetupV3());
            pstp.setParseType(new OrcParserProvider.OrcParserInfo());
            ParseSetup ps = ParseSetup.guessSetup(new Key[]{nfs._key}, pstp);
            Assert.assertEquals(ps.getParseType().name(), "ORC");
            ps.getColumnTypes()[0] = Vec.T_BAD;
            ps.getColumnTypes()[1] = Vec.T_CAT;
            ps.getColumnTypes()[2] = Vec.T_STR;
            ParseSetup ps2 = ParseSetup.guessSetup(new Key[]{nfs._key}, ps);
            String errs = Arrays.deepToString(ps2.errs());
            Assert.assertEquals(1,ps2.errs().length);
            Assert.assertTrue(ps2.errs()[0] instanceof ParseWriter.UnsupportedTypeOverride);
            System.out.println("types: " + Arrays.toString(ArrayUtils.select(Vec.TYPE_STR,ps2.getColumnTypes())));
            Key k = Key.make();
            Job<Frame> j = ParseDataset.forkParseDataset(k,new Key[]{nfs._key},ps,true)._job;
            Frame fr = Scope.track(j.get());
            String  warns = Arrays.toString(j.warns());
            Assert.assertEquals(errs,warns);
            Assert.assertArrayEquals(new String[]{"bar", "cat", "dog", "eat", "foo", "zebra"},fr.vec(1).domain());
            Assert.assertTrue(fr.vec(0).isBad());
        } finally {
            Scope.exit();
        }
    }

    @Test
    public void testParseAllOrcs() {
        try {
            Set<String> failedFiles = new TreeSet<>();
            for (String fileName : allOrcFiles) {
                Log.info("Orc Parser parsing " + fileName);
                File f = locateFile(fileName);

                if (f != null && f.exists()) {
                    try {
                        numberWrong += compareOrcAndH2OFrame(fileName, f, failedFiles);
                        totalFilesTested++;
                    } catch (IOException e) {
                        e.printStackTrace();
                        failedFiles.add(fileName);
                        numberWrong++;
                    }
                } else {
                    Log.warn("The following file was not found: " + fileName);
                    failedFiles.add(fileName);
                    numberWrong++;
                }
            }

            if (numberWrong > 0) {
                Log.warn("There are errors in your test.");
                assertEquals("Number of orc files failed to parse is: " + numberWrong + ", failed files = " +
                    failedFiles.toString(), 0, numberWrong);
            } else {
                Log.info("Parser test passed!  Number of files parsed is " + totalFilesTested);
            }
        } finally {
            Scope.exit();
        }
    }
}
