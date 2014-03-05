package io.druid.query.aggregation.hyperloglog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Charsets;
import com.metamx.common.IAE;
import io.druid.query.aggregation.Aggregator;
import io.druid.query.aggregation.AggregatorFactory;
import io.druid.query.aggregation.BufferAggregator;
import io.druid.query.aggregation.NoopAggregator;
import io.druid.query.aggregation.NoopBufferAggregator;
import io.druid.segment.ColumnSelectorFactory;
import io.druid.segment.ObjectColumnSelector;
import org.apache.commons.codec.binary.Base64;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 */
public class HyperUniquesAggregatorFactory implements AggregatorFactory
{
  public static Object estimateCardinality(Object object)
  {
    if (object == null) {
      return 0;
    }

    return ((HyperLogLogCollector) object).estimateCardinality();
  }

  private static final byte CACHE_TYPE_ID = 0x5;

  private final String name;
  private final String fieldName;

  @JsonCreator
  public HyperUniquesAggregatorFactory(
      @JsonProperty("name") String name,
      @JsonProperty("fieldName") String fieldName
  )
  {
    this.name = name;
    this.fieldName = fieldName.toLowerCase();
  }

  @Override
  public Aggregator factorize(ColumnSelectorFactory metricFactory)
  {
    ObjectColumnSelector selector = metricFactory.makeObjectColumnSelector(fieldName);

    if (selector == null) {
      return new NoopAggregator(name);
    }

    if (HyperLogLogCollector.class.isAssignableFrom(selector.classOfObject())) {
      return new HyperUniquesAggregator(name, selector);
    }

    throw new IAE(
        "Incompatible type for metric[%s], expected a HyperUnique, got a %s", fieldName, selector.classOfObject()
    );
  }

  @Override
  public BufferAggregator factorizeBuffered(ColumnSelectorFactory metricFactory)
  {
    ObjectColumnSelector selector = metricFactory.makeObjectColumnSelector(fieldName);

    if (selector == null) {
      return new NoopBufferAggregator();
    }

    if (HyperLogLogCollector.class.isAssignableFrom(selector.classOfObject())) {
      return new HyperUniquesBufferAggregator(selector);
    }

    throw new IAE(
        "Incompatible type for metric[%s], expected a HyperUnique, got a %s", fieldName, selector.classOfObject()
    );
  }

  @Override
  public Comparator getComparator()
  {
    return new Comparator<HyperLogLogCollector>()
    {
      @Override
      public int compare(HyperLogLogCollector lhs, HyperLogLogCollector rhs)
      {
        return lhs.compareTo(rhs);
      }
    };
  }

  @Override
  public Object combine(Object lhs, Object rhs)
  {
    if (rhs == null) {
      return lhs;
    }
    if (lhs == null) {
      return rhs;
    }
    return ((HyperLogLogCollector) lhs).fold((HyperLogLogCollector) rhs);
  }

  @Override
  public AggregatorFactory getCombiningFactory()
  {
    return new HyperUniquesAggregatorFactory(name, name);
  }

  @Override
  public Object deserialize(Object object)
  {
    if (object instanceof byte[]) {
      return HyperLogLogCollector.makeCollector(ByteBuffer.wrap((byte[]) object));
    } else if (object instanceof ByteBuffer) {
      return HyperLogLogCollector.makeCollector((ByteBuffer) object);
    } else if (object instanceof String) {
      return HyperLogLogCollector.makeCollector(
          ByteBuffer.wrap(Base64.decodeBase64(((String) object).getBytes(Charsets.UTF_8)))
      );
    }
    return object;
  }

  @Override

  public Object finalizeComputation(Object object)
  {
    return estimateCardinality(object);
  }

  @Override
  @JsonProperty
  public String getName()
  {
    return name;
  }

  @Override
  public List<String> requiredFields()
  {
    return Arrays.asList(fieldName);
  }

  @JsonProperty
  public String getFieldName()
  {
    return fieldName;
  }

  @Override
  public byte[] getCacheKey()
  {
    byte[] fieldNameBytes = fieldName.getBytes(Charsets.UTF_8);

    return ByteBuffer.allocate(1 + fieldNameBytes.length).put(CACHE_TYPE_ID).put(fieldNameBytes).array();
  }

  @Override
  public String getTypeName()
  {
    return "hyperUnique";
  }

  @Override
  public int getMaxIntermediateSize()
  {
    return HyperLogLogCollector.getLatestNumBytesForDenseStorage();
  }

  @Override
  public Object getAggregatorStartValue()
  {
    return HyperLogLogCollector.makeLatestCollector();
  }

  @Override
  public String toString()
  {
    return "HyperUniquesAggregatorFactory{" +
           "name='" + name + '\'' +
           ", fieldName='" + fieldName + '\'' +
           '}';
  }
}
