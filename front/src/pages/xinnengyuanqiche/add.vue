<template>
	<div class="add-update-preview">
		<el-form
			class="add-update-form"
			ref="ruleForm"
			:model="ruleForm"
			:rules="rules"
			label-width="180px"
			>
			<el-form-item class="add-item" label="套餐名称" prop="qichexinghao">
				<el-input v-model="ruleForm.qichexinghao" 
					placeholder="套餐名称" clearable :disabled=" false  ||ro.qichexinghao"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="套餐类型" prop="qicheleixing">
				<el-input v-model="ruleForm.qicheleixing" 
					placeholder="套餐类型" clearable :disabled=" false  ||ro.qicheleixing"></el-input>
			</el-form-item>
			<el-form-item class="add-item"  label="品牌" prop="pinpai">
				<el-select v-model="ruleForm.pinpai" placeholder="请选择品牌" :disabled=" false  ||ro.pinpai" >
					<el-option
						v-for="(item,index) in pinpaiOptions"
						:key="index"
						:label="item"
						:value="item">
					</el-option>
				</el-select>
			</el-form-item>
			<el-form-item class="add-item" label="单次时长" prop="baigonglijiasu">
				<el-input v-model="ruleForm.baigonglijiasu" 
					placeholder="单次时长" clearable :disabled=" false  ||ro.baigonglijiasu"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="建议间隔" prop="zuigaoshisu">
				<el-input v-model="ruleForm.zuigaoshisu" 
					placeholder="建议间隔" clearable :disabled=" false  ||ro.zuigaoshisu"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="套餐有效期" prop="xuhanggonglishu">
				<el-input v-model="ruleForm.xuhanggonglishu" 
					placeholder="套餐有效期" clearable :disabled=" false  ||ro.xuhanggonglishu"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="适用肤质" prop="xiaolv">
				<el-input v-model="ruleForm.xiaolv" 
					placeholder="适用肤质" clearable :disabled=" false  ||ro.xiaolv"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="套餐价格" prop="jiage">
				<el-input-number v-model="ruleForm.jiage" placeholder="套餐价格" :disabled=" false ||ro.jiage"></el-input-number>
			</el-form-item>
			<el-form-item class="add-item" label="服务次数" prop="zuoweishu">
				<el-input v-model.number="ruleForm.zuoweishu" 
					placeholder="服务次数" clearable :disabled=" false  ||ro.zuoweishu"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="包含项目" prop="donglizongcheng">
				<el-input v-model="ruleForm.donglizongcheng" 
					placeholder="包含项目" clearable :disabled=" false  ||ro.donglizongcheng"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="预约方式" prop="chongdianchatou">
				<el-input v-model="ruleForm.chongdianchatou" 
					placeholder="预约方式" clearable :disabled=" false  ||ro.chongdianchatou"></el-input>
			</el-form-item>
			<el-form-item class="add-item" label="套餐封面" v-if="type!='cross' || (type=='cross' && !ro.fengmian)" prop="fengmian">
				<file-upload
					tip="点击上传套餐封面"
					action="file/upload"
					:limit="3"
					:multiple="true"
					:fileUrls="ruleForm.fengmian?ruleForm.fengmian:''"
					@change="fengmianUploadChange"
					></file-upload>
			</el-form-item>
			<el-form-item class="add-item" v-else label="套餐封面" prop="fengmian">
				<img v-if="ruleForm.fengmian.substring(0,4)=='http'" class="upload-img" v-bind:key="index" :src="ruleForm.fengmian.split(',')[0]">
				<img v-else class="upload-img" v-bind:key="index" v-for="(item,index) in ruleForm.fengmian.split(',')" :src="baseUrl+item">
			</el-form-item>
			<el-form-item class="add-item" label="套餐亮点" prop="waixingshiyang">
				<el-input
					type="textarea"
					:rows="8"
					placeholder="套餐亮点"
					v-model="ruleForm.waixingshiyang">
					</el-input>
			</el-form-item>
			<el-form-item class="add-item" label="使用说明" prop="chongdianfangan">
				<el-input
					type="textarea"
					:rows="8"
					placeholder="使用说明"
					v-model="ruleForm.chongdianfangan">
					</el-input>
			</el-form-item>
			<el-form-item class="add-item" label="注意事项" prop="jishuguige">
				<el-input
					type="textarea"
					:rows="8"
					placeholder="注意事项"
					v-model="ruleForm.jishuguige">
					</el-input>
			</el-form-item>
			<el-form-item class="add-item" label="详细介绍" prop="xiangxijieshao">
				<editor 
					v-model="ruleForm.xiangxijieshao" 
					class="editor" 
					action="file/upload">
				</editor>
			</el-form-item>

			<el-form-item class="add-btn-item">
				<el-button class="submitBtn"  type="primary" @click="onSubmit">
					<span class="icon iconfont "></span>
					<span class="text">提交</span>
				</el-button>
				<el-button class="closeBtn" @click="back()">
					<span class="icon iconfont "></span>
					<span class="text">取消</span>
				</el-button>
			</el-form-item>
		</el-form>
	</div>
</template>

<script>
	export default {
		data() {
			return {
				id: '',
				baseUrl: '',
				ro:{
					qichexinghao : false,
					qicheleixing : false,
					pinpai : false,
					baigonglijiasu : false,
					zuigaoshisu : false,
					xuhanggonglishu : false,
					xiaolv : false,
					jiage : false,
					zuoweishu : false,
					donglizongcheng : false,
					chongdianchatou : false,
					fengmian : false,
					waixingshiyang : false,
					chongdianfangan : false,
					jishuguige : false,
					xiangxijieshao : false,
					clicktime : false,
					clicknum : false,
					storeupnum : false,
				},
				type: '',
				userTableName: localStorage.getItem('UserTableName'),
				ruleForm: {
					qichexinghao: '',
					qicheleixing: '',
					pinpai: '',
					baigonglijiasu: '',
					zuigaoshisu: '',
					xuhanggonglishu: '',
					xiaolv: '',
					jiage: '',
					zuoweishu: '',
					donglizongcheng: '',
					chongdianchatou: '',
					fengmian: '',
					waixingshiyang: '',
					chongdianfangan: '',
					jishuguige: '',
					xiangxijieshao: '',
					clicktime: '',
					clicknum: '',
					storeupnum: '',
				},
				pinpaiOptions: [],


				rules: {
					qichexinghao: [
					],
					qicheleixing: [
					],
					pinpai: [
					],
					baigonglijiasu: [
					],
					zuigaoshisu: [
					],
					xuhanggonglishu: [
					],
					xiaolv: [
					],
					jiage: [
						{ validator: this.$validate.isNumber, trigger: 'blur' },
					],
					zuoweishu: [
						{ validator: this.$validate.isIntNumer, trigger: 'blur' },
					],
					donglizongcheng: [
					],
					chongdianchatou: [
					],
					fengmian: [
					],
					waixingshiyang: [
					],
					chongdianfangan: [
					],
					jishuguige: [
					],
					xiangxijieshao: [
					],
					clicktime: [
					],
					clicknum: [
						{ validator: this.$validate.isIntNumer, trigger: 'blur' },
					],
					storeupnum: [
						{ validator: this.$validate.isIntNumer, trigger: 'blur' },
					],
				},
				centerType: false,
			};
		},
		computed: {



		},
		components: {
		},
		created() {
			if(this.$route.query.centerType){
				this.centerType = true
			}
			//this.bg();
			let type = this.$route.query.type ? this.$route.query.type : '';
			this.init(type);
			this.baseUrl = this.$config.baseUrl;
		},
		methods: {
			getMakeZero(s) {
				return s < 10 ? '0' + s : s;
			},
			// 下载
			download(file){
				window.open(`${file}`)
			},
			// 初始化
			init(type) {
				this.type = type;
				if(type=='cross'){
					var obj = JSON.parse(localStorage.getItem('crossObj'));
					for (var o in obj){
						if(o=='qichexinghao'){
							this.ruleForm.qichexinghao = obj[o];
							this.ro.qichexinghao = true;
							continue;
						}
						if(o=='qicheleixing'){
							this.ruleForm.qicheleixing = obj[o];
							this.ro.qicheleixing = true;
							continue;
						}
						if(o=='pinpai'){
							this.ruleForm.pinpai = obj[o];
							this.ro.pinpai = true;
							continue;
						}
						if(o=='baigonglijiasu'){
							this.ruleForm.baigonglijiasu = obj[o];
							this.ro.baigonglijiasu = true;
							continue;
						}
						if(o=='zuigaoshisu'){
							this.ruleForm.zuigaoshisu = obj[o];
							this.ro.zuigaoshisu = true;
							continue;
						}
						if(o=='xuhanggonglishu'){
							this.ruleForm.xuhanggonglishu = obj[o];
							this.ro.xuhanggonglishu = true;
							continue;
						}
						if(o=='xiaolv'){
							this.ruleForm.xiaolv = obj[o];
							this.ro.xiaolv = true;
							continue;
						}
						if(o=='jiage'){
							this.ruleForm.jiage = obj[o];
							this.ro.jiage = true;
							continue;
						}
						if(o=='zuoweishu'){
							this.ruleForm.zuoweishu = obj[o];
							this.ro.zuoweishu = true;
							continue;
						}
						if(o=='donglizongcheng'){
							this.ruleForm.donglizongcheng = obj[o];
							this.ro.donglizongcheng = true;
							continue;
						}
						if(o=='chongdianchatou'){
							this.ruleForm.chongdianchatou = obj[o];
							this.ro.chongdianchatou = true;
							continue;
						}
						if(o=='fengmian'){
							this.ruleForm.fengmian = obj[o].split(",")[0];
							this.ro.fengmian = true;
							continue;
						}
						if(o=='waixingshiyang'){
							this.ruleForm.waixingshiyang = obj[o];
							this.ro.waixingshiyang = true;
							continue;
						}
						if(o=='chongdianfangan'){
							this.ruleForm.chongdianfangan = obj[o];
							this.ro.chongdianfangan = true;
							continue;
						}
						if(o=='jishuguige'){
							this.ruleForm.jishuguige = obj[o];
							this.ro.jishuguige = true;
							continue;
						}
						if(o=='xiangxijieshao'){
							this.ruleForm.xiangxijieshao = obj[o];
							this.ro.xiangxijieshao = true;
							continue;
						}
						if(o=='clicktime'){
							this.ruleForm.clicktime = obj[o];
							this.ro.clicktime = true;
							continue;
						}
						if(o=='clicknum'){
							this.ruleForm.clicknum = obj[o];
							this.ro.clicknum = true;
							continue;
						}
						if(o=='storeupnum'){
							this.ruleForm.storeupnum = obj[o];
							this.ro.storeupnum = true;
							continue;
						}
					}
				}else if(type=='edit'){
					this.info()
				}
				// 获取用户信息
				this.$http.get(this.userTableName + '/session', {emulateJSON: true}).then(res => {
					if (res.data.code == 0) {
						var json = res.data.data;
					}
				});
				this.$http.get('option/pinpaixinxi/pinpai', {emulateJSON: true}).then(res => {
					if (res.data.code == 0) {
						this.pinpaiOptions = res.data.data;
					}
				});

				if (localStorage.getItem('raffleType') && localStorage.getItem('raffleType') != null) {
					localStorage.removeItem('raffleType')
					setTimeout(() => {
						this.onSubmit()
					}, 300)
				}
			},

			// 多级联动参数
			// 多级联动参数
			info() {
				this.$http.get(`xinnengyuanqiche/detail/${this.$route.query.id}`, {emulateJSON: true}).then(res => {
					if (res.data.code == 0) {
						this.ruleForm = res.data.data;
					}
				});
			},
			// 提交
			async onSubmit() {
				await this.$refs["ruleForm"].validate(async valid => {
					if(valid) {
						if(this.type=='cross'){
							var statusColumnName = localStorage.getItem('statusColumnName');
							var statusColumnValue = localStorage.getItem('statusColumnValue');
							if(statusColumnName && statusColumnName!='') {
								var obj = JSON.parse(localStorage.getItem('crossObj'));
								if(!statusColumnName.startsWith("[")) {
									for (var o in obj){
										if(o==statusColumnName){
											obj[o] = statusColumnValue;
										}
									}
									var table = localStorage.getItem('crossTable');
									await this.$http.post(table+'/update', obj).then(res => {});
								}
							}
						}


						await this.$http.post(`xinnengyuanqiche/${this.ruleForm.id?'update':this.centerType?'save':'add'}`, this.ruleForm).then(async res => {
							if (res.data.code == 0) {
								this.$message({
									message: '操作成功',
									type: 'success',
									duration: 1500,
									onClose: () => {
										this.$router.go(-1);
										
									}
								});
							} else {
								this.$message({
									message: res.data.msg,
									type: 'error',
									duration: 1500
								});
							}
						});
					}
				});
			},
			// 获取uuid
			getUUID () {
				return new Date().getTime();
			},
			// 返回
			back() {
				this.$router.go(-1);
			},
			fengmianUploadChange(fileUrls) {
				this.ruleForm.fengmian = fileUrls.replace(new RegExp(this.$config.baseUrl,"g"),"");
			},
		}
	};
</script>

<style rel="stylesheet/scss" lang="scss" scoped>
	.add-update-preview {
		padding: 0 0 20px;
		margin: 0px auto;
		color: #666;
		background: #fff;
		width: 1200px;
		font-size: 16px;
		position: relative;
		.add-update-form {
			margin: 20px 0 0;
			width: 100%;
			position: relative;
			.add-item.el-form-item {
				border-radius: 0px;
				padding: 6px 0 0;
				margin: 0 0 20px 0;
				background: none;
				border-color: #475a8310;
				border-width:  0 0 0px;
				border-style: solid;
				::v-deep .el-form-item__label {
					padding: 0 10px 0 0;
					color: #666;
					font-weight: 500;
					width: 180px;
					font-size: inherit;
					line-height: 40px;
					text-align: right;
				}
				::v-deep .el-form-item__content {
					margin-left: 180px;
				}
				.el-input {
					width: auto;
				}
				.el-input ::v-deep .el-input__inner {
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 0 12px;
					box-shadow: none;
					color: inherit;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-input ::v-deep .el-input__inner[readonly="readonly"] {
					border: 0;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 0 12px;
					box-shadow: none;
					color: rgba(85, 85, 127, 1.0);
					background: none;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-input-number ::v-deep .el-input__inner {
					text-align: left;
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 0 12px;
					box-shadow: none;
					color: inherit;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-input-number ::v-deep .is-disabled .el-input__inner {
					text-align: left;
					border: 0;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 0 12px;
					box-shadow: none;
					color: rgba(85, 85, 127, 1.0);
					background: none;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-input-number ::v-deep .el-input-number__decrease {
					display: none;
				}
				.el-input-number ::v-deep .el-input-number__increase {
					display: none;
				}
				.el-select {
					width: auto;
				}
				.el-select ::v-deep .el-input__inner {
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 0 10px;
					color: inherit;
					width: 100%;
					font-size: 16px;
					min-width: inherit !important;
					height: 40px;
				}
				.el-select ::v-deep .is-disabled .el-input__inner {
					border: 0;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 0 10px;
					box-shadow: none;
					color: inherit;
					background: none;
					width: 100%;
					font-size: 16px;
					height: 40px;
				}
				.el-date-editor {
					width: auto;
				}
				.el-date-editor ::v-deep .el-input__inner {
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 0 10px 0 30px;
					box-shadow: none;
					color: inherit;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				.el-date-editor ::v-deep .el-input__inner[readonly="readonly"] {
					border: 0;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 0 10px 0 30px;
					box-shadow: none;
					color: inherit;
					background: none;
					width: auto;
					font-size: 16px;
					height: 40px;
				}
				::v-deep .el-upload--picture-card {
					background: transparent;
					border: 0;
					border-radius: 0;
					width: auto;
					height: auto;
					line-height: initial;
					vertical-align: middle;
				}
				::v-deep .upload .upload-img {
					border: 1px solid #ddd;
					cursor: pointer;
					border-radius: 0px;
					color: #999;
					background: #fff;
					width: 80px;
					font-size: 26px;
					line-height: 60px;
					text-align: center;
					height: 60px;
				}
				::v-deep .el-upload-list .el-upload-list__item {
					border: 1px solid #ddd;
					cursor: pointer;
					border-radius: 0px;
					color: #999;
					background: #fff;
					width: 80px;
					font-size: 26px;
					line-height: 60px;
					text-align: center;
					height: 60px;
					font-size: 14px;
					line-height: 1.8;
				}
				::v-deep .el-upload .el-icon-plus {
					border: 1px solid #ddd;
					cursor: pointer;
					border-radius: 0px;
					color: #999;
					background: #fff;
					width: 80px;
					font-size: 26px;
					line-height: 60px;
					text-align: center;
					height: 60px;
				}
				::v-deep .el-upload__tip {
					color: #888;
					font-size: 16px;
				}
				.el-textarea ::v-deep .el-textarea__inner {
					border: 1px solid #ddd;
					border-radius: 0px;
					padding: 12px;
					box-shadow: none;
					color: inherit;
					width: auto;
					font-size: 16px;
					min-height: 150px;
					min-width: 48%;
					height: auto;
				}
				.el-textarea ::v-deep .el-textarea__inner[readonly="readonly"] {
					border: 0px solid #ddd;
					cursor: not-allowed;
					border-radius: 0px;
					padding: 12px;
					box-shadow: none;
					color: inherit;
					background: none;
					width: auto;
					font-size: 16px;
					min-height: 150px;
					min-width: 50%;
					height: auto;
				}
				::v-deep .el-input__inner::placeholder {
					color: inherit;
					font-size: inherit;
				}
				::v-deep textarea::placeholder {
					color: inherit;
					font-size: inherit;
				}
				.editor {
					background-color: #fff;
					border-radius: 0;
					padding: 0;
					box-shadow: none;
					margin: 0;
					width: 100%;
					min-height: 350px;
					border-color: #ccc;
					border-width: 1px;
					border-style: solid;
					height: auto;
				}
				.upload-img {
					object-fit: cover;
					width: 100px;
					height: 100px;
				}
				.viewBtn {
					border: 0;
					cursor: pointer;
					border-radius: 4px;
					padding: 0 20px;
					margin: 0;
					color: #333;
					background: #475a8330;
					display: inline-block;
					width: auto;
					font-size: 14px;
					line-height: 34px;
					height: 34px;
				}
				.viewBtn:hover {
				}
				.unviewBtn {
					border: 0;
					cursor: pointer;
					padding: 0 20px;
					margin: 0;
					color: #333;
					display: inline-block;
					font-size: 14px;
					line-height: 34px;
					border-radius: 4px;
					outline: none;
					background: #ddd;
					width: auto;
					height: 34px;
				}
				.unviewBtn:hover {
				}
			}
			.add-btn-item {
				padding: 0;
				margin: 20px 0;
				.submitBtn {
					border: 0;
					cursor: pointer;
					padding: 0 24px 0 30px;
					margin: 0 20px 0 0;
					display: inline-block;
					font-size: 16px;
					line-height: 44px;
					border-radius: 4px;
					background: #0066D4;
					width: auto;
					text-align: center;
					min-width: 120px;
					height: 44px;
					.icon {
						color: #fff;
					}
					.text {
						color: #fff;
					}
				}
				.submitBtn:hover {
					color: #fff;
					.icon {
					}
					.text {
					}
				}
				.closeBtn {
					border: 1px solid #0066D450;
					cursor: pointer;
					padding: 0 24px 0 30px;
					margin: 0 20px 0 0;
					color: #0066D4;
					display: inline-block;
					font-size: 16px;
					line-height: 44px;
					border-radius: 4px;
					background: #fff;
					width: auto;
					text-align: center;
					min-width: 120px;
					height: 44px;
					.icon {
						color: #333;
					}
					.text {
						color: #0066D4;
					}
				}
				.closeBtn:hover {
					color: #80593c;
					.icon {
					}
					.text {
					}
				}
			}
		}
	}
	.el-date-editor.el-input {
		width: auto;
	}
</style>
