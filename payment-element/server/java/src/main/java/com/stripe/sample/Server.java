package com.stripe.sample;

import java.util.HashMap;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.staticFiles;
import static spark.Spark.port;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;

import com.stripe.Stripe;
import com.stripe.model.*;
import com.stripe.exception.*;
import com.stripe.net.Webhook;
import com.stripe.param.*;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Data;

public class Server {
    private static Gson gson = new Gson();

    static class ConfigResponse {
        private String publishableKey;

        public ConfigResponse(String publishableKey) {
            this.publishableKey = publishableKey;
        }
    }

    static class FailureResponse {
        private HashMap<String, String> error;

        public FailureResponse(String message) {
            this.error = new HashMap<String, String>();
            this.error.put("message", message);
        }
    }

    static class CreatePaymentResponse {
        private String clientSecret;
        private String customerId;

        public CreatePaymentResponse(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public CreatePaymentResponse(String clientSecret, String customerId) {
            this.clientSecret = clientSecret;
            this.customerId = customerId;
        }
    }

    @Data
    static class SubscriptionRequest {
        private String paymentMethodId;
        private String priceId;
        private String customerId;
        private String email;
    }

    public static void main(String[] args) {
        port(4242);
        Dotenv dotenv = Dotenv.load();

        Stripe.apiKey = dotenv.get("STRIPE_SECRET_KEY");

        // For sample support and debugging, not required for production:
        Stripe.setAppInfo(
            "stripe-samples/accept-a-payment/payment-element",
            "0.0.1",
            "https://github.com/stripe-samples"
        );

        staticFiles.externalLocation(
          Paths.get(
            Paths.get("").toAbsolutePath().toString(),
            dotenv.get("STATIC_DIR")
          ).normalize().toString());

        get("/config", (request, response) -> {
            response.type("application/json");

            return gson.toJson(new ConfigResponse(dotenv.get("STRIPE_PUBLISHABLE_KEY")));
        });

        get("/create-payment-intent", (request, response) -> {
            response.type("application/json");

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAutomaticPaymentMethods(
                      PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                              .setEnabled(true)
                              .build()
                    )
                    .setCurrency("EUR")
                    .setAmount(1999L)
                    .build();

            try {
              // Create a PaymentIntent with the order amount and currency
              PaymentIntent intent = PaymentIntent.create(params);
              System.out.println("Payment Intent Created: " + gson.toJson(intent));

              // Send PaymentIntent details to client
              return gson.toJson(new CreatePaymentResponse(intent.getClientSecret()));
            } catch(StripeException e) {
              response.status(400);
              return gson.toJson(new FailureResponse(e.getMessage()));
            } catch(Exception e) {
              response.status(500);
              return gson.toJson(e);
            }
        });


        get("create-setup-intent", (request, response) -> {
            String customerName = "guzicheng";
            String customerEmail = "guzicheng@thinredline.com.cn";

            // 创建或获取客户
            Customer customer = Customer.list(
                    CustomerListParams.builder()
                            .setLimit(1L)
                            .setEmail(customerEmail)
                            .build()
                    )
                    .getData().stream().findFirst().orElse(null);

            if (customer == null) {
                if (request.params("customerId") != null) {
                    customer = Customer.retrieve(request.params("customerId") );
                }
                else {
                    CustomerCreateParams customerParams = CustomerCreateParams.builder()
                            .setName(customerName)
                            .setEmail(customerEmail)
//                            .setPaymentMethod(params.getPaymentMethodId())
//                            .setInvoiceSettings(
//                                    CustomerCreateParams.InvoiceSettings.builder()
//                                            .setDefaultPaymentMethod(params.getPaymentMethodId())
//                                            .build()
//                            )
                            .build();
                    customer = Customer.create(customerParams);
                }
            }
            System.out.println("Customer Retrieved: " + gson.toJson(customer));

            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                    .setCustomer(customer.getId())
//                    .addPaymentMethodType(InvoiceCreateParams.PaymentSettings.PaymentMethodType.CARD.getValue())
                    .build();

            try {
                // Create a SetupIntent with the order amount and currency
                SetupIntent intent = SetupIntent.create(params);
                System.out.println("Setup Intent Created: " + gson.toJson(intent));

                // Send SetupIntent details to client
                return gson.toJson(new CreatePaymentResponse(intent.getClientSecret(), customer.getId()));
            } catch(StripeException e) {
                response.status(400);
                return gson.toJson(new FailureResponse(e.getMessage()));
            } catch(Exception e) {
                response.status(500);
                return gson.toJson(e);
            }
        });

        post("/create-subscription", (request, response) -> {

            SubscriptionRequest params = gson.fromJson(request.body(), SubscriptionRequest.class);
            if (params.getCustomerId() == null || params.getPriceId() == null) {
                response.status(400);
                return gson.toJson(new FailureResponse("未知Customer or 未知Price"));
            }

            try {
                // 创建订阅
                Map<String, Object> subscriptionParams = new HashMap<>();
                subscriptionParams.put("customer", params.getCustomerId());
                subscriptionParams.put("items", ImmutableList.of(ImmutableMap.of(
                        "price", params.getPriceId()
                )));
                subscriptionParams.put("payment_behavior", "default_incomplete"); // 前端确认付款stripe.confirmPayment
//                subscriptionParams.put("payment_behavior", "error_if_incomplete"); // 后端自动创建并收款，复用验证记录
                if(params.getPaymentMethodId() != null) subscriptionParams.put("default_payment_method", params.getPaymentMethodId());
                subscriptionParams.put("payment_settings", ImmutableMap.of(
                        "save_default_payment_method", "on_subscription"
                ));
                subscriptionParams.put("expand", ImmutableList.of("latest_invoice.payment_intent"));

                Subscription subscription = Subscription.create(subscriptionParams);
                String paymentIntentClientSecret = Optional.ofNullable(subscription)
                        .map(Subscription::getLatestInvoiceObject)
                        .map(Invoice::getPaymentIntentObject)
                        .map(PaymentIntent::getClientSecret)
                        .orElse(null);
                System.out.println("Subscription Created: " + gson.toJson(subscription));
                System.out.println("Payment Intent ClientSecret: " + paymentIntentClientSecret);


                // 返回订阅信息
                return gson.toJson(new CreatePaymentResponse(paymentIntentClientSecret));
            } catch(StripeException e) {
                response.status(400);
                return gson.toJson(new FailureResponse(e.getMessage()));
            } catch(Exception e) {
                response.status(500);
                return gson.toJson(new FailureResponse(e.getMessage()));
            }
        });

        post("/webhook", (request, response) -> {
            String payload = request.body();
            String sigHeader = request.headers("Stripe-Signature");
            String endpointSecret = dotenv.get("STRIPE_WEBHOOK_SECRET");

            Event event = null;

            try {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } catch (SignatureVerificationException e) {
                // Invalid signature
                response.status(400);
                return "";
            }


            System.out.println(String.format("Webhook received [%s]: %s", event.getType(), event.getData().toString()));

            switch (event.getType()) {
                case "payment_intent.succeeded":
                    // Fulfill any orders, e-mail receipts, etc
                    // To cancel the payment you will need to issue a Refund
                    // (https://stripe.com/docs/api/refunds)
                    System.out.println("💰Payment received!");
                    break;
                case "payment_intent.payment_failed":
                    System.out.println("❌ Payment failed.");
                    break;
                default:
                    // Unexpected event type
                    response.status(400);
                    return "";
            }

            response.status(200);
            return "";
        });
    }
}
