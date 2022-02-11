package org.apache.pulsar.io.core.transform;

import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.*;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.schema.GenericObject;
import org.apache.pulsar.functions.api.Record;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.testng.Assert.assertEquals;

public class RenameFieldTests
{
    class TestRecord implements Record
    {
        Schema schema;
        Optional<String> key;
        Object value;

        TestRecord(Schema schema, Optional<String> key, Object value) {
            this.schema = schema;
            this.key = key;
            this.value = value;
        }

        @Override
        public Schema getSchema()
        {
            return schema;
        }

        @Override
        public Optional<String> getKey()
        {
            return key;
        }

        @Override
        public Object getValue()
        {
            return value;
        }
    }


    @Test
    public void testRename() throws Exception
    {
        org.apache.avro.Schema schemaX = org.apache.avro.Schema.createRecord("x", "x", "ns1", false,
                ImmutableList.of(
                        new org.apache.avro.Schema.Field("x1", org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING)),
                        new org.apache.avro.Schema.Field("x2", org.apache.avro.Schema.create(org.apache.avro.Schema.Type.INT))));
        org.apache.avro.Schema schemaY = org.apache.avro.Schema.createRecord("y", "y", "ns1", false,
                ImmutableList.of(
                        new org.apache.avro.Schema.Field("y1", org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING)),
                        new org.apache.avro.Schema.Field("y2", org.apache.avro.Schema.create(org.apache.avro.Schema.Type.INT))));
        org.apache.avro.Schema rootSchema = org.apache.avro.Schema.createRecord("r", "r", "ns1", false,
                ImmutableList.of(
                        new org.apache.avro.Schema.Field("x", schemaX),
                        new org.apache.avro.Schema.Field("y", schemaY),
                new org.apache.avro.Schema.Field("a", org.apache.avro.Schema.create(org.apache.avro.Schema.Type.STRING))));

        org.apache.avro.generic.GenericRecord genericRecordX = new org.apache.avro.generic.GenericData.Record(schemaX);
        genericRecordX.put("x1", "xx1");
        genericRecordX.put("x2", 2);
        org.apache.avro.generic.GenericRecord genericRecordY = new org.apache.avro.generic.GenericData.Record(schemaY);
        genericRecordY.put("y1", "yy1");
        genericRecordY.put("y2", 3);
        org.apache.avro.generic.GenericRecord genericRecord = new org.apache.avro.generic.GenericData.Record(rootSchema);
        genericRecord.put("a", "aaa");
        genericRecord.put("x", genericRecordX);
        genericRecord.put("y", genericRecordY);

        TestRecord testRecord = new TestRecord(new AvroSchemaWrapper(rootSchema), Optional.of("key1"), deserialize(serialize(genericRecord, rootSchema), rootSchema));

        RenameFields renameFields = new RenameFields();
        renameFields.init(ImmutableMap.of("type","value","renames","a:b,x.x1:x.xx1"));

        Record result = renameFields.apply(testRecord);

        GenericObject genericObject = (GenericObject) result.getValue();
        GenericData.Record outGenericRecord = (GenericData.Record) genericObject.getNativeObject();
        assertEquals("aaa", outGenericRecord.get("b").toString());
        GenericData.Record outGenericRecordX = (GenericData.Record) outGenericRecord.get("x");
        assertEquals("xx1", outGenericRecordX.get("xx1").toString());
        assertEquals(2, outGenericRecordX.get("x2"));
        GenericData.Record outGenericRecordY = (GenericData.Record) outGenericRecord.get("y");
        assertEquals("yy1", outGenericRecordY.get("y1").toString());
        assertEquals(3, outGenericRecordY.get("y2"));
    }

    public static byte[] serialize(GenericRecord record, org.apache.avro.Schema schema) throws IOException {
        SpecificDatumWriter<GenericRecord> datumWriter = new SpecificDatumWriter<>(schema);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        BinaryEncoder binaryEncoder = new EncoderFactory().binaryEncoder(byteArrayOutputStream, null);
        datumWriter.write(record, binaryEncoder);
        binaryEncoder.flush();
        return byteArrayOutputStream.toByteArray();
    }

    public static GenericRecord  deserialize(byte[] recordBytes, org.apache.avro.Schema schema) throws IOException {
        DatumReader<GenericRecord> datumReader = new SpecificDatumReader(schema);
        ByteArrayInputStream stream = new ByteArrayInputStream(recordBytes);
        BinaryDecoder binaryDecoder = new DecoderFactory().binaryDecoder(stream, null);
        return datumReader.read(null, binaryDecoder);
    }
}
