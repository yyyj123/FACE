<template>
	<div>
			<div class="login-container oc-admin-login">
			<el-form class="login_form animate__animated animate__fadeInDown">
					<div class="login_form2 oc-admin-login__panel">
						<div class="title-container">服务运营控制台<span>安全登录后进入管理工作台</span></div>
					<div v-if="loginType==1" class="list-item">
							<label class="lable" for="admin-username">
								账号：
							</label>
							<input id="admin-username" autocomplete="username" placeholder="请输入账号" name="username" type="text" v-model="rulesForm.username">
					</div>
					<div v-if="loginType==1" class="list-item">
							<label class="lable" for="admin-password">
								密码：
							</label>
						<div class="password-box">
								<input id="admin-password" autocomplete="current-password" placeholder="请输入密码" name="password" :type="showPassword?'text':'password'" v-model="rulesForm.password">
								<button type="button" class="icon iconfont" :class="showPassword?'icon-liulan13':'icon-liulan17'" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword=!showPassword"></button>
						</div>
					</div>

						<div class="list-item identity-item">
							<label class="lable">登录身份：</label>
							<el-radio-group v-model="rulesForm.role" class="identity-switch" aria-label="选择登录身份">
								<el-radio-button v-for="item in loginRoles" :key="item.value" :label="item.value">{{ item.label }}</el-radio-button>
							</el-radio-group>
						</div>

		
					<div class="login-btn">
						<div class="login-btn1">
							<el-button v-if="loginType==1" type="primary" @click="login()" class="loginInBt">登录</el-button>
						</div>
						<div class="login-btn2">
						</div>
						<div class="login-btn3">
						</div>
					</div>
				</div>
			</el-form>
		</div>
	</div>
</template>
<script>
	import 'animate.css'
		export default {
		data() {
			return {
				verifyCheck2: false,
				flag: false,
				baseUrl:this.$base.url,
				loginType: 1,
				rulesForm: {
					username: "",
					password: "",
						role: "ADMIN",
					},
					loginRoles: [
						{ label: '管理员', value: 'ADMIN' },
						{ label: '技师', value: 'BEAUTICIAN' },
					],
					tableName: "",
				showPassword: false,
			};
		},
			mounted() {},
		created() {

		},
		destroyed() {
		},
		components: {
		},
		methods: {

			//注册
			register(tableName){
				this.$storage.set("loginTable", tableName);
				this.$router.push({path:'/register',query:{pageFlag:'register'}})
			},
			// 登陆
			login() {

				if (!this.rulesForm.username) {
					this.$message.error("请输入用户名");
					return;
				}
				if (!this.rulesForm.password) {
					this.$message.error("请输入密码");
					return;
				}
					if (!this.rulesForm.role) {
						this.$message.error("请选择登录身份");
						return;
					}
					this.tableName = 'account';
		
				this.loginPost()
			},
				loginPost() {
					this.$http({
						url: `api/v1/auth/login`,
						method: "post",
						data: {
							username: this.rulesForm.username,
							password: this.rulesForm.password
						}
					}).then(({ data }) => {
						if (data && data.code === 0) {
							const actualRole = data.data.role;
							const isBeautician = actualRole === 'BEAUTICIAN';
							const isAdmin = ['OWNER', 'MANAGER', 'FRONT_DESK', 'ADMIN'].includes(actualRole);
							const identityMatches = this.rulesForm.role === 'BEAUTICIAN' ? isBeautician : isAdmin;
							if (!identityMatches) {
								this.$message.error(this.rulesForm.role === 'BEAUTICIAN' ? '该账号不是技师账号，请选择管理员登录' : '该账号不是管理员账号，请选择技师登录');
								return;
							}
							if(this.$storage.get('nowLocation')){
							let location = this.$storage.get('nowLocation')
							this.$storage.set('beforeLocation',location)
						}
						if(this.$storage.get('nowTime')){
							let times = this.$storage.get('nowTime')
							this.$storage.set('beforeTime',times)
						}
							this.$storage.set("Token", data.data.token);
							const roleNames = {OWNER: '管理员', MANAGER: '管理员', FRONT_DESK: '前台', BEAUTICIAN: '美容师'};
							this.$storage.set("role", roleNames[data.data.role] || data.data.role);
							this.$storage.set("apiRole", data.data.role);
							this.$storage.set("sessionTable", "account");
						this.$storage.set("adminName", this.rulesForm.username);
						this.$router.replace({ path: "/" });
					} else {
						this.$message.error(data.msg);
					}
				});
			},
		}
	}
</script>

<style lang="scss" scoped>
.login-container {
	min-height: 100vh;
	position: relative;
	background-repeat: no-repeat;
	background-position: center center;
	background-size: cover;
	background: url(http://codegen.caihongy.cn/20240808/4690bab5e91648408e9ba44aedb3ccb2.jpg);
	background-repeat: no-repeat;
	background-size: cover;
	background: url(http://codegen.caihongy.cn/20240808/4690bab5e91648408e9ba44aedb3ccb2.jpg);
	display: flex;
	width: 100%;
	min-height: 100vh;
	justify-content: flex-end;
	align-items: center;
	background-position: center center;
	position: relative;

	.login_form {
		padding: 0;
		margin: 0;
		background: none;
		display: flex;
		width: 100%;
		min-height: 100vh;
		justify-content: flex-end;
		align-items: center;
		position: relative;
		.login_form2 {
			border-radius: 10px;
			padding: 30px 20px 20px;
			box-shadow: -10px 0px 13px -7px #000000, 10px 0px 13px -7px #000000, 5px 5px 15px 5px rgba(0,0,0,0);
			margin: 0 20% 0 0;
			background: #4dc57b;
			width: 550px;
			min-height: 320px;
		}
		.title-container {
			padding: 0 8% 0 18%;
			margin: 20px  20px 0;
			color: #000;
			font-weight: 600;
			font-size: 32px;
			line-height: 50px;
			border-radius: 8px;
			top: 0;
			left: 0;
			background: none;
			width: calc(78% - 500px);
			position: absolute;
			text-align: left;
		}
		.list-item {
			padding: 0;
			margin: 0 0 20px 120px;
			display: flex;
			width: calc(90% - 120px);
			align-items: center;
			position: relative;
			flex-wrap: wrap;
			.lable {
				color: #fff;
				left: -120px;
				width: 120px;
				font-size: 16px;
				line-height: 40px;
				position: absolute !important;
				text-align: right;
			}
			input {
				border: 1px solid rgba(255, 255, 255, .5);
				padding: 0 10px;
				color: #fff;
				background: none;
				width: 100%;
				font-size: 16px;
				height: 36px;
			}
			input:focus {
				border: 1px solid rgba(0, 0, 0, .5);
				border-radius: 0;
				padding: 0 10px;
				color: #fff;
				background: none;
				width: 100%;
				font-size: 16px;
				height: 34px;
			}
			.password-box {
				display: flex;
				width: 100%;
				position: relative;
				align-items: center;
				input {
					border: 1px solid rgba(255, 255, 255, .5);
					padding: 0 10px;
					color: rgba(64, 158, 255, 1);
					background: none;
					width: 100%;
					font-size: 16px;
					height: 36px;
				}
				input:focus {
					border: 1px solid rgba(0, 0, 0, .5);
					padding: 0 10px;
					color: #fff;
					background: none;
					width: 100%;
					font-size: 16px;
					height: 36px;
				}
				.iconfont {
					cursor: pointer;
					z-index: 1;
					color: #fff;
					top: 0;
					font-size: 16px;
					line-height: 44px;
					position: absolute;
					right: 5px;
				}
			}
			input::placeholder {
				color: rgba(255, 255, 255, .6);
				font-size: 16px;
			}
			::v-deep .el-select {
				width: 100%;
			}
			::v-deep .el-select .el-input__inner {
				border: 1px solid rgba(255, 255, 255, .5);
				border-radius: 0;
				padding: 0 10px;
				color: #fff;
				background: none;
				width: 100%;
				font-size: 16px;
				height: 36px;
			}
			::v-deep .el-select .is-focus .el-input__inner {
				border: 1px solid rgba(0, 0, 0, .5);
				border-radius: 0;
				padding: 0 10px;
				color: #fff;
				background: none;
				width: 100%;
				font-size: 16px;
				height: 36px;
			}
			::v-deep .el-select .el-input__inner::placeholder{
				color: rgba(255, 255, 255, .6);
				font-size: 16px;
			}
		}
		.login-btn {
			margin: 20px auto 0 120px;
			display: flex;
			width: calc(90% - 120px);
			align-items: center;
			flex-wrap: wrap;
			.login-btn1 {
				margin: 0 5px 10px 0;
				width: auto;
			}
			.login-btn2 {
				width: auto;
			}
			.login-btn3 {
				width: auto;
			}
			.loginInBt {
				border: 0px solid rgba(255, 255, 255, .5);
				cursor: pointer;
				border-radius: 4px;
				padding: 0 10px;
				margin: 0;
				color: rgba(0, 0, 0, 1);
				background: #fff;
				width: 100%;
				font-size: 16px;
				min-width: 68px;
				height: 40px;
			}
			.loginInBt:hover {
				border: 0px solid rgba(85, 170, 0, 1.0);
				color: #00ad45;
				background: #fff;
				opacity: 0.8;
			}
			.register {
				border: 1px solid rgba(255, 255, 255, 1);
				cursor: pointer;
				border-radius: 4px;
				padding: 0 10px;
				margin: 0 5px 10px 0;
				color: rgba(255, 255, 255, 1);
				background: none;
				width: auto;
				font-size: 16px;
				height: 34px;
			}
			.register:hover {
				opacity: 0.8;
			}
			.forget {
				border: 0;
				cursor: pointer;
				border-radius: 0;
				padding: 0;
				margin: 0 5px 10px 0;
				color: #fff;
				background: none;
				width: auto;
				font-size: 16px;
				height: 34px;
			}
			.forget:hover {
				opacity: 1;
			}
		}
	}
}

</style>
