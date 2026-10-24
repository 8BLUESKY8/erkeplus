package gitee.leolin_me.erkepp.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** 作业附件的类型编号，照官方 H5 的 getFileType：1 图片、3 Word、4 Excel、5 PDF、6 其它。 */
class AttachmentTypeTest {

    @Test
    fun `images are type one`() {
        assertEquals(1, attachmentTypeOf("image/png"))
        assertEquals(1, attachmentTypeOf("image/gif"))
        assertEquals(1, attachmentTypeOf("image/jpeg"))
    }

    @Test
    fun `office and pdf map to their own numbers`() {
        assertEquals(3, attachmentTypeOf("application/msword"))
        assertEquals(
            3,
            attachmentTypeOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        )
        assertEquals(4, attachmentTypeOf("application/vnd.ms-excel"))
        assertEquals(
            4,
            attachmentTypeOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        )
        assertEquals(5, attachmentTypeOf("application/pdf"))
    }

    @Test
    fun `everything else falls back to six`() {
        assertEquals(6, attachmentTypeOf("application/zip"))
        assertEquals(6, attachmentTypeOf("text/plain"))
        assertEquals(6, attachmentTypeOf(""))
    }
}
