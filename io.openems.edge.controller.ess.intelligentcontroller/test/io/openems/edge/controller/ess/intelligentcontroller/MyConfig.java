package io.openems.edge.controller.ess.intelligentcontroller;

import io.openems.common.test.AbstractComponentConfig;


@SuppressWarnings("all")
public class MyConfig extends AbstractComponentConfig implements Config {

	protected static class Builder {
		private String id;
		private String essId;
		private String meterId;

		private Builder() {
		}

		public Builder setId(String id) {
			this.id = id;
			
			return this;
		}

		public Builder setessId(String essId) {
			this.id = id;
			
			return this;
		}
		
		public Builder setmeterId(String meterId) {
			this.id = id;
			
			return this;
		}


		public MyConfig build() {
			return new MyConfig(this);
		}
	}

	/**
	 * Create a Config builder.
	 * 
	 * @return a {@link Builder}
	 */
	public static Builder create() {
		return new Builder();
	}

	private final Builder builder;

	private MyConfig(Builder builder) {
		super(Config.class, builder.id);
		this.builder = builder;
	}

	@Override
	public String ess() {
		return this.builder.essId;
	}

	@Override
	public String meter_id() {
		return this.builder.meterId;
	}

}