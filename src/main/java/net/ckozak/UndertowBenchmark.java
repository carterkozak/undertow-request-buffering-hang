package net.ckozak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.google.common.base.Preconditions.checkState;

@Measurement(iterations = 3, time = 3)
@Warmup(iterations = 10, time = 5)
@Fork(value = 1)
public class UndertowBenchmark {

    public static final int NUMBER_OF_NUMBERS_SMALL = 3200;
    public static final List<Integer> NUMBERS_SMALL = IntStream.rangeClosed(0, NUMBER_OF_NUMBERS_SMALL)
            .boxed().collect(Collectors.toList());

    public static final Request POST_NUMBERS_REQUEST_SMALL = makeRequestBuilder("/postNumbers").post(
            RequestBody.create(MediaType.parse("application/json"), createListByte(NUMBERS_SMALL))
    ).build();

    private static byte[] createListByte(List<Integer> numbers) {
        try {
            return new ObjectMapper().writeValueAsBytes(numbers);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException();
        }
    }


    @Threads(32)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void parallelRequestsPostNumbersSmall(BenchmarkState state) {
        runRequest(state, POST_NUMBERS_REQUEST_SMALL);
    }

    @Path("/")
    public interface BenchmarkService {
        @POST
        @Path("/postNumbers")
        int postNumbers(List<Integer> numbers);
    }

    public static class BenchmarkResource implements BenchmarkService {
        @Override
        public int postNumbers(List<Integer> numbers) {
            return numbers.stream().reduce((num1, num2) -> num1 + num2).get();
        }
    }

    private static void runRequest(BenchmarkState state, Request request) {
        try (Response response = state.getOkHttpClient().newCall(request).execute()) {
            checkState(response.isSuccessful());
            ByteStreams.copy(response.body().byteStream(), ByteStreams.nullOutputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Request.Builder makeRequestBuilder(String endpoint) {
        return new Request.Builder()
                .header(HttpHeaders.ACCEPT, "application/json")
                .url("https://localhost:" + BenchmarkState.PORT + "/api" + endpoint);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder().include(UndertowBenchmark.class.getSimpleName()).build();
        new Runner(opt).run();
    }
}
