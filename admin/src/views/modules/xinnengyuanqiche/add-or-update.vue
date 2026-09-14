<template>
	<div class="addEdit-block oc-form-section">
		<el-form
			class="add-update-preview oc-admin-form"
			ref="ruleForm"
			:model="ruleForm"
			:rules="rules"
			label-width="180px"
		>
			<template >
				<el-form-item class="input" v-if="type!='info'"  label="套餐名称" prop="qichexinghao" >
					<el-input v-model="ruleForm.qichexinghao" placeholder="套餐名称" clearable  :readonly="ro.qichexinghao"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="套餐名称" prop="qichexinghao" >
					<el-input v-model="ruleForm.qichexinghao" placeholder="套餐名称" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="套餐类型" prop="qicheleixing" >
					<el-input v-model="ruleForm.qicheleixing" placeholder="套餐类型" clearable  :readonly="ro.qicheleixing"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="套餐类型" prop="qicheleixing" >
					<el-input v-model="ruleForm.qicheleixing" placeholder="套餐类型" readonly></el-input>
				</el-form-item>
					<el-form-item class="select" v-if="type!='info'"  label="品牌" prop="pinpai" >
						<el-select :disabled="ro.pinpai" v-model="ruleForm.pinpai" placeholder="请选择品牌" >
						<el-option
							v-for="(item,index) in pinpaiOptions"
							v-bind:key="index"
							:label="item"
							:value="item">
						</el-option>
					</el-select>
				</el-form-item>
					<el-form-item v-else class="input" label="品牌" prop="pinpai" >
						<el-input v-model="ruleForm.pinpai"
							placeholder="品牌" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="单次时长" prop="baigonglijiasu" >
					<el-input v-model="ruleForm.baigonglijiasu" placeholder="单次时长" clearable  :readonly="ro.baigonglijiasu"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="单次时长" prop="baigonglijiasu" >
					<el-input v-model="ruleForm.baigonglijiasu" placeholder="单次时长" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="建议间隔" prop="zuigaoshisu" >
					<el-input v-model="ruleForm.zuigaoshisu" placeholder="建议间隔" clearable  :readonly="ro.zuigaoshisu"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="建议间隔" prop="zuigaoshisu" >
					<el-input v-model="ruleForm.zuigaoshisu" placeholder="建议间隔" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="套餐有效期" prop="xuhanggonglishu" >
					<el-input v-model="ruleForm.xuhanggonglishu" placeholder="套餐有效期" clearable  :readonly="ro.xuhanggonglishu"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="套餐有效期" prop="xuhanggonglishu" >
					<el-input v-model="ruleForm.xuhanggonglishu" placeholder="套餐有效期" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="适用肤质" prop="xiaolv" >
					<el-input v-model="ruleForm.xiaolv" placeholder="适用肤质" clearable  :readonly="ro.xiaolv"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="适用肤质" prop="xiaolv" >
					<el-input v-model="ruleForm.xiaolv" placeholder="适用肤质" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="套餐价格" prop="jiage" >
					<el-input-number v-model="ruleForm.jiage" placeholder="套餐价格" :disabled="ro.jiage"></el-input-number>
				</el-form-item>
				<el-form-item v-else class="input" label="套餐价格" prop="jiage" >
					<el-input v-model="ruleForm.jiage" placeholder="套餐价格" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="服务次数" prop="zuoweishu" >
					<el-input v-model.number="ruleForm.zuoweishu" placeholder="服务次数" clearable  :readonly="ro.zuoweishu"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="服务次数" prop="zuoweishu" >
					<el-input v-model="ruleForm.zuoweishu" placeholder="服务次数" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="包含项目" prop="donglizongcheng" >
					<el-input v-model="ruleForm.donglizongcheng" placeholder="包含项目" clearable  :readonly="ro.donglizongcheng"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="包含项目" prop="donglizongcheng" >
					<el-input v-model="ruleForm.donglizongcheng" placeholder="包含项目" readonly></el-input>
				</el-form-item>
				<el-form-item class="input" v-if="type!='info'"  label="预约方式" prop="chongdianchatou" >
					<el-input v-model="ruleForm.chongdianchatou" placeholder="预约方式" clearable  :readonly="ro.chongdianchatou"></el-input>
				</el-form-item>
				<el-form-item v-else class="input" label="预约方式" prop="chongdianchatou" >
					<el-input v-model="ruleForm.chongdianchatou" placeholder="预约方式" readonly></el-input>
				</el-form-item>
				<el-form-item class="upload" v-if="type!='info' && !ro.fengmian" label="套餐封面" prop="fengmian" >
					<file-upload
						tip="点击上传套餐封面"
						action="file/upload"
						:limit="3"
						:multiple="true"
						:fileUrls="ruleForm.fengmian?ruleForm.fengmian:''"
						@change="fengmianUploadChange"
					></file-upload>
				</el-form-item>
				<el-form-item class="upload" v-else-if="ruleForm.fengmian" label="套餐封面" prop="fengmian" >
					<img v-if="ruleForm.fengmian.substring(0,4)=='http'" class="upload-img" style="margin-right:20px;" v-bind:key="index" :src="ruleForm.fengmian.split(',')[0]" :alt="`${ruleForm.qichexinghao || '护理套餐'}套餐封面`" width="100" height="100">
					<img v-else class="upload-img" style="margin-right:20px;" v-bind:key="index" v-for="(item,index) in ruleForm.fengmian.split(',')" :src="$base.url+item" :alt="`${ruleForm.qichexinghao || '护理套餐'}套餐封面 ${index + 1}`" width="100" height="100">
				</el-form-item>
			</template>
			<el-form-item class="textarea" v-if="type!='info'" label="套餐亮点" prop="waixingshiyang" >
				<el-input
					style="min-width: 200px; max-width: 600px;"
					type="textarea"
					:rows="8"
					placeholder="套餐亮点"
					v-model="ruleForm.waixingshiyang" >
				</el-input>
			</el-form-item>
			<el-form-item v-else-if="ruleForm.waixingshiyang" label="套餐亮点" prop="waixingshiyang" >
				<span class="text">{{ruleForm.waixingshiyang}}</span>
			</el-form-item>
			<el-form-item class="textarea" v-if="type!='info'" label="使用说明" prop="chongdianfangan" >
				<el-input
					style="min-width: 200px; max-width: 600px;"
					type="textarea"
					:rows="8"
					placeholder="使用说明"
					v-model="ruleForm.chongdianfangan" >
				</el-input>
			</el-form-item>
			<el-form-item v-else-if="ruleForm.chongdianfangan" label="使用说明" prop="chongdianfangan" >
				<span class="text">{{ruleForm.chongdianfangan}}</span>
			</el-form-item>
			<el-form-item class="textarea" v-if="type!='info'" label="注意事项" prop="jishuguige" >
				<el-input
					style="min-width: 200px; max-width: 600px;"
					type="textarea"
					:rows="8"
					placeholder="注意事项"
					v-model="ruleForm.jishuguige" >
				</el-input>
			</el-form-item>
			<el-form-item v-else-if="ruleForm.jishuguige" label="注意事项" prop="jishuguige" >
				<span class="text">{{ruleForm.jishuguige}}</span>
			</el-form-item>
			<el-form-item v-if="type!='info'"  label="详细介绍" prop="xiangxijieshao" >
				<editor 
					style="min-width: 200px; max-width: 600px;"
					v-model="ruleForm.xiangxijieshao" 
					class="editor" 
					action="file/upload">
				</editor>
			</el-form-item>
			<el-form-item v-else-if="ruleForm.xiangxijieshao" label="详细介绍" prop="xiangxijieshao" >
				<span class="text" v-html="ruleForm.xiangxijieshao"></span>
			</el-form-item>
			<el-form-item class="btn">
				<el-button class="btn3"  v-if="type!='info'" type="success" @click="onSubmit">
					<span class="icon iconfont icon-xihuan"></span>
					提交
				</el-button>
				<el-button class="btn4" v-if="type!='info'" type="success" @click="back()">
					<span class="icon iconfont icon-xihuan"></span>
					取消
				</el-button>
				<el-button class="btn5" v-if="type=='info'" type="success" @click="back()">
					<span class="icon iconfont icon-xihuan"></span>
					返回
				</el-button>
			</el-form-item>
		</el-form>
    

	</div>
</template>
<script>
	import { 
		isNumber,
		isIntNumer,
	} from "@/utils/validate";
	export default {
		data() {
			var validateNumber = (rule, value, callback) => {
				if(!value){
					callback();
				} else if (!isNumber(value)) {
					callback(new Error("请输入数字"));
				} else {
					callback();
				}
			};
			var validateIntNumber = (rule, value, callback) => {
				if(!value){
					callback();
				} else if (!isIntNumer(value)) {
					callback(new Error("请输入整数"));
				} else {
					callback();
				}
			};
			return {
				id: '',
				type: '',
			
			
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
						{ validator: validateNumber, trigger: 'blur' },
					],
					zuoweishu: [
						{ validator: validateIntNumber, trigger: 'blur' },
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
						{ validator: validateIntNumber, trigger: 'blur' },
					],
					storeupnum: [
						{ validator: validateIntNumber, trigger: 'blur' },
					],
				},
			};
		},
		props: ["parent"],
		computed: {



		},
		components: {
		},
		created() {
		},
		methods: {
			// 下载
			download(file){
				window.open(`${file}`)
			},
			// 初始化
			init(id,type) {
				if (id) {
					this.id = id;
					this.type = type;
				}
				if(this.type=='info'||this.type=='else'){
					this.info(id);
				}else if(this.type=='logistics'){
					this.logistics=false;
					this.info(id);
				}else if(this.type=='cross'){
					var obj = this.$storage.getObj('crossObj');
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
							this.ruleForm.fengmian = obj[o];
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
				}
				// 获取用户信息
				this.$http({
					url: `${this.$storage.get('sessionTable')}/session`,
					method: "get"
				}).then(({ data }) => {
					if (data && data.code === 0) {
						var json = data.data;
					} else {
						this.$message.error(data.msg);
					}
				});
				this.$http({
					url: `option/pinpaixinxi/pinpai`,
					method: "get"
				}).then(({ data }) => {
					if (data && data.code === 0) {
						this.pinpaiOptions = data.data;
					} else {
						this.$message.error(data.msg);
					}
				});
			
			},
			// 多级联动参数

			info(id) {
				this.$http({
					url: `xinnengyuanqiche/info/${id}`,
					method: "get"
				}).then(({ data }) => {
					if (data && data.code === 0) {
						this.ruleForm = data.data;
						//解决前台上传图片后台不显示的问题
						let reg=new RegExp('../../../upload','g')//g代表全部
						this.ruleForm.xiangxijieshao = this.ruleForm.xiangxijieshao.replace(reg,'../../../car/upload');
					} else {
						this.$message.error(data.msg);
					}
				});
			},

			// 提交
			async onSubmit() {
					if(this.ruleForm.fengmian!=null) {
						this.ruleForm.fengmian = this.ruleForm.fengmian.replace(new RegExp(this.$base.url,"g"),"");
					}
					var objcross = this.$storage.getObj('crossObj');
					await this.$refs["ruleForm"].validate(async valid => {
						if (valid) {
							if(this.type=='cross'){
								var statusColumnName = this.$storage.get('statusColumnName');
								var statusColumnValue = this.$storage.get('statusColumnValue');
								if(statusColumnName!='') {
									var obj = this.$storage.getObj('crossObj');
									if(statusColumnName && !statusColumnName.startsWith("[")) {
										for (var o in obj){
											if(o==statusColumnName){
												obj[o] = statusColumnValue;
											}
										}
										var table = this.$storage.get('crossTable');
										await this.$http({
											url: `${table}/update`,
											method: "post",
											data: obj
										}).then(({ data }) => {});
									}
								}
							}
							
							await this.$http({
								url: `xinnengyuanqiche/${!this.ruleForm.id ? "save" : "update"}`,
								method: "post",
								data: this.ruleForm
							}).then(async ({ data }) => {
								if (data && data.code === 0) {
									this.$message({
										message: "操作成功",
										type: "success",
										duration: 1500,
										onClose: () => {
											this.parent.showFlag = true;
											this.parent.addOrUpdateFlag = false;
											this.parent.xinnengyuanqicheCrossAddOrUpdateFlag = false;
											this.parent.search();
											this.parent.contentStyleChange();
										}
									});
								} else {
									this.$message.error(data.msg);
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
				this.parent.showFlag = true;
				this.parent.addOrUpdateFlag = false;
				this.parent.xinnengyuanqicheCrossAddOrUpdateFlag = false;
				this.parent.contentStyleChange();
			},
			fengmianUploadChange(fileUrls) {
				this.ruleForm.fengmian = fileUrls;
			},
		}
	};
</script>
<style lang="scss" scoped>
	.addEdit-block {
		padding: 30px;
	}
	.add-update-preview {
		border-radius: 12px;
		padding: 30px;
		background: rgba(255,255,255,0);
		border-color: #ddd;
		border-width: 1px;
		border-style: solid;
	}
	.amap-wrapper {
		width: 100%;
		height: 500px;
	}
	
	.search-box {
		position: absolute;
	}
	
	.el-date-editor.el-input {
		width: auto;
	}
	.add-update-preview ::v-deep .el-form-item {
		border: 0px solid #eee;
		padding: 2px 20px;
		margin: 0 0 20px 0;
		display: inline-block;
		width: 48%;
	}
	.add-update-preview .el-form-item ::v-deep .el-form-item__label {
		padding: 0 10px 0 0;
		color: #666;
		font-weight: 500;
		width: 180px;
		font-size: 16px;
		line-height: 40px;
		text-align: right;
	}
	
	.add-update-preview .el-form-item ::v-deep .el-form-item__content {
		margin-left: 180px;
	}
	.add-update-preview .el-form-item span.text {
		color: #666;
		font-weight: 500;
		display: inline-block;
		font-size: 16px;
		line-height: 40px;
	}
	
	.add-update-preview .el-input {
		width: 100%;
	}
	.add-update-preview .el-input ::v-deep .el-input__inner {
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 0 12px;
		color: #666;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-input ::v-deep .el-input__inner[readonly="readonly"] {
		border: 0px solid #ccc;
		cursor: not-allowed;
		border-radius: 0px;
		padding: 0 12px;
		color: #666;
		background: none;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-input-number {
		text-align: left;
		width: 100%;
	}
	.add-update-preview .el-input-number ::v-deep .el-input__inner {
		text-align: left;
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 0 12px;
		color: #666;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-input-number ::v-deep .is-disabled .el-input__inner {
		text-align: left;
		border: 0px solid #ccc;
		cursor: not-allowed;
		border-radius: 0px;
		padding: 0 12px;
		color: #666;
		background: none;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-input-number ::v-deep .el-input-number__decrease {
		display: none;
	}
	.add-update-preview .el-input-number ::v-deep .el-input-number__increase {
		display: none;
	}
	.add-update-preview .el-select {
		width: auto;
	}
	.add-update-preview .el-select ::v-deep .el-input__inner {
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 0 10px;
		color: #666;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-select ::v-deep .is-disabled .el-input__inner {
		border: 0;
		cursor: not-allowed;
		border-radius: 4px;
		padding: 0 10px;
		color: #666;
		background: none;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-date-editor {
		width: auto;
	}
	.add-update-preview .el-date-editor ::v-deep .el-input__inner {
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 0 10px 0 30px;
		color: #666;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .el-date-editor ::v-deep .el-input__inner[readonly="readonly"] {
		border: 0;
		cursor: not-allowed;
		border-radius: 0px;
		padding: 0 10px 0 30px;
		color: #666;
		background: none;
		width: auto;
		font-size: 16px;
		height: 34px;
	}
	.add-update-preview .viewBtn {
		border: 1px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		padding: 0 15px;
		margin: 0 20px 0 0;
		color: #666;
		background: linear-gradient(180deg, rgba(255,255,255,1) 0%, rgba(238,238,238,1) 100%);
		width: auto;
		font-size: 16px;
		line-height: 34px;
		height: 34px;
		.iconfont {
			margin: 0 2px;
			color: #666;
			font-size: 14px;
			height: 34px;
		}
	}
	.add-update-preview .viewBtn:hover {
		opacity: 0.8;
	}
	.add-update-preview .downBtn {
		border: 1px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		padding: 0 15px;
		margin: 0 20px 0 0;
		color: #666;
		background: linear-gradient(180deg, rgba(255,255,255,1) 0%, rgba(238,238,238,1) 100%);
		width: auto;
		font-size: 16px;
		line-height: 34px;
		height: 34px;
		.iconfont {
			margin: 0 2px;
			color: #666;
			font-size: 16px;
			height: 34px;
		}
	}
	.add-update-preview .downBtn:hover {
		opacity: 0.8;
	}
	.add-update-preview .unBtn {
		border: 0;
		cursor: not-allowed;
		border-radius: 4px;
		padding: 0 0px;
		margin: 0 20px 0 0;
		outline: none;
		color: #999;
		background: none;
		width: auto;
		font-size: 16px;
		line-height: 34px;
		height: 34px;
		.iconfont {
			margin: 0 2px;
			color: #fff;
			display: none;
			font-size: 14px;
			height: 34px;
		}
	}
	.add-update-preview .unBtn:hover {
		opacity: 0.8;
	}
	.add-update-preview ::v-deep .el-upload--picture-card {
		background: transparent;
		border: 0;
		border-radius: 0;
		width: auto;
		height: auto;
		line-height: initial;
		vertical-align: middle;
	}
	
	.add-update-preview ::v-deep .upload .upload-img {
		border: 0px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		color: #666;
		background: #fff;
		object-fit: cover;
		width: 90px;
		font-size: 24px;
		line-height: 60px;
		text-align: center;
		height: 60px;
	}
	
	.add-update-preview ::v-deep .el-upload-list .el-upload-list__item {
		border: 0px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		color: #666;
		background: #fff;
		object-fit: cover;
		width: 90px;
		font-size: 24px;
		line-height: 60px;
		text-align: center;
		height: 60px;
	}
	
	.add-update-preview ::v-deep .el-upload .el-icon-plus {
		border: 0px solid #ccc;
		cursor: pointer;
		border-radius: 0px;
		color: #666;
		background: #fff;
		object-fit: cover;
		width: 90px;
		font-size: 24px;
		line-height: 60px;
		text-align: center;
		height: 60px;
	}
	.add-update-preview ::v-deep .el-upload__tip {
		color: #838fa1;
		font-size: 16px;
	}
	
	.add-update-preview .el-textarea ::v-deep .el-textarea__inner {
		border: 0px solid #ccc;
		border-radius: 0px;
		padding: 12px;
		color: #666;
		background: #fff;
		width: auto;
		font-size: 16px;
		min-width: 400px;
		height: 120px;
	}
	.add-update-preview .el-textarea ::v-deep .el-textarea__inner[readonly="readonly"] {
				border: 0;
				cursor: not-allowed;
				border-radius: 0px;
				padding: 12px;
				color: #666;
				background: none;
				width: auto;
				font-size: 16px;
				min-width: 400px;
				height: auto;
			}
	.add-update-preview .el-form-item.btn {
		padding: 0;
		margin: 20px 0 0;
		.btn1 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 40px;
			}
		}
		.btn1:hover {
			opacity: 0.8;
		}
		.btn2 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 34px;
			}
		}
		.btn2:hover {
			opacity: 0.8;
		}
		.btn3 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 40px;
			}
		}
		.btn3:hover {
			opacity: 0.8;
		}
		.btn4 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 40px;
			}
		}
		.btn4:hover {
			opacity: 0.8;
		}
		.btn5 {
			border: 0px solid #ccc;
			cursor: pointer;
			border-radius: 4px;
			padding: 0 10px;
			margin: 0 10px 0 0;
			color: #fff;
			background: #00ad45;
			width: auto;
			font-size: 16px;
			min-width: 110px;
			height: 40px;
			.iconfont {
				margin: 0 2px;
				color: #fff;
				display: none;
				font-size: 14px;
				height: 40px;
			}
		}
		.btn5:hover {
			opacity: 0.8;
		}
	}
</style>
